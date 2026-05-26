;; tts.clj — Babashka TTS DSL for Supertonic
;;
;; Usage:
;;   (require '[tts :refer [perform say pause laugh breath sigh
;;                          M1 M2 M3 M4 M5 F1 F2 F3 F4 F5
;;                          with-voice with-speed with-lang dialog]])
;;
;;   (perform
;;     (say M1 "Hello!" (laugh) "Good to see you.")
;;     (say F1 "Thanks!" (breath) "Let's get started."))
;;
;;   (perform
;;     (dialog
;;       (M1 "Knock knock.")
;;       (F1 "Who's there?")
;;       (M1 "Babashka.")
;;       (F1 "Babashka who?")
;;       (M1 (laugh) "Babashka your pardon, I didn't mean to interrupt!")))

(ns tts
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [babashka.process :as proc]
            [clojure.java.io :as io]))

;; --- Connection ---

(def ^:dynamic *server* {:host "127.0.0.1" :port 7788})
(def ^:dynamic *debug* false)

(def http-client (http/client {:version :http1.1}))

(defn base-url []
  (str "http://" (:host *server*) ":" (:port *server*)))

(def pid-dir (str (or (System/getenv "XDG_RUNTIME_DIR") "/tmp") "/bb-tts"))

(defn pid-file []
  (str pid-dir "/server-" (:port *server*) ".pid"))

(defn server-healthy? []
  (try
    (http/get (str (base-url) "/v1/health") {:client http-client})
    true
    (catch Exception _ false)))

(defn ensure-server []
  (when-not (server-healthy?)
    (when *debug* (binding [*out* *err*] (println "Starting supertonic server...")))
    (.mkdirs (io/file pid-dir))
    (let [p (proc/process ["supertonic-serve"
                           "--host" (:host *server*)
                           "--port" (str (:port *server*))]
                          {:out (if *debug* :inherit (io/file "/dev/null"))
                           :err (if *debug* :inherit (io/file "/dev/null"))})]
      (spit (pid-file) (.pid (:proc p)))
      (loop [attempts 60]
        (cond
          (server-healthy?) true
          (pos? attempts)   (do (Thread/sleep 500) (recur (dec attempts)))
          :else             (throw (ex-info "Server failed to start" {})))))))

;; --- Segment data model ---
;;
;; A segment is a map:
;;   {:text "Hello <laugh>" :voice "M1" :lang "en" :speed 1.0}
;;
;; DSL functions produce segments or fragments (strings that get
;; concatenated into a segment's :text).

(def ^:dynamic *voice* "M1")
(def ^:dynamic *speed* 1.0)
(def ^:dynamic *lang* "en")

;; Expression tags — synthesized as their own segments for best quality
(defn laugh  [] {:type :expression :tag "<laugh>"})
(defn breath [] {:type :expression :tag "<breath>"})
(defn sigh   [] {:type :expression :tag "<sigh>"})

;; Pause — generates PCM silence
(defn pause [seconds]
  {:type :pause :seconds seconds})

;; Voice constructors — each returns a function that builds segments.
;; Expression tags and pauses split text into separate segments.
(defn- build-segments [voice lang speed parts]
  (let [resolved (map #(if (fn? %) (%) %) parts)]
    (loop [remaining resolved
           current-text []
           result []]
      (if (empty? remaining)
        (if (seq current-text)
          (conj result {:voice voice :lang lang :speed speed
                        :text (clojure.string/join " " current-text)})
          result)
        (let [part (first remaining)]
          (cond
            ;; Pause — flush text, insert silence
            (and (map? part) (= :pause (:type part)))
            (let [segs (if (seq current-text)
                         (conj result {:voice voice :lang lang :speed speed
                                       :text (clojure.string/join " " current-text)})
                         result)]
              (recur (rest remaining) [] (conj segs part)))

            ;; Expression tag — flush text, insert as speech segment
            (and (map? part) (= :expression (:type part)))
            (let [segs (if (seq current-text)
                         (conj result {:voice voice :lang lang :speed speed
                                       :text (clojure.string/join " " current-text)})
                         result)]
              (recur (rest remaining) []
                     (conj segs {:voice voice :lang lang :speed speed
                                 :text (:tag part)})))

            ;; Plain text
            :else
            (recur (rest remaining) (conj current-text part) result)))))))

(defn- make-voice [name]
  (fn [& parts]
    (build-segments name *lang* *speed* parts)))

(def M1 (make-voice "M1"))
(def M2 (make-voice "M2"))
(def M3 (make-voice "M3"))
(def M4 (make-voice "M4"))
(def M5 (make-voice "M5"))
(def F1 (make-voice "F1"))
(def F2 (make-voice "F2"))
(def F3 (make-voice "F3"))
(def F4 (make-voice "F4"))
(def F5 (make-voice "F5"))

(defn say
  "Build segments. First arg can be a voice fn or uses *voice*."
  [voice-or-text & parts]
  (if (fn? voice-or-text)
    (apply voice-or-text parts)
    (build-segments *voice* *lang* *speed* (cons voice-or-text parts))))

(defmacro with-voice [voice & body]
  `(binding [*voice* ~voice] (vec (flatten (list ~@body)))))

(defmacro with-speed [speed & body]
  `(binding [*speed* ~speed] (vec (flatten (list ~@body)))))

(defmacro with-lang [lang & body]
  `(binding [*lang* ~lang] (vec (flatten (list ~@body)))))

(defn dialog
  "Takes segment maps and returns them as a flat sequence."
  [& segments]
  (vec (flatten segments)))

;; --- Synthesis and playback ---

(defn synthesize
  "Send text to the TTS server, return WAV bytes."
  [{:keys [text voice lang speed]}]
  (let [payload (json/generate-string
                  {:text text
                   :voice (or voice *voice*)
                   :lang (or lang *lang*)
                   :speed (or speed *speed*)
                   :response_format "wav"})
        resp (http/post (str (base-url) "/v1/tts")
                        {:client http-client
                         :headers {"content-type" "application/json"}
                         :body payload
                         :as :bytes})]
    (:body resp)))

(defn- read-chunk-id [^bytes wav i]
  (str (char (bit-and (aget wav i) 0xFF))
       (char (bit-and (aget wav (+ i 1)) 0xFF))
       (char (bit-and (aget wav (+ i 2)) 0xFF))
       (char (bit-and (aget wav (+ i 3)) 0xFF))))

(defn- read-u32-le [^bytes wav i]
  (bit-or (bit-and (aget wav i) 0xFF)
          (bit-shift-left (bit-and (aget wav (+ i 1)) 0xFF) 8)
          (bit-shift-left (bit-and (aget wav (+ i 2)) 0xFF) 16)
          (bit-shift-left (bit-and (aget wav (+ i 3)) 0xFF) 24)))

(defn- wav-data-offset
  "Find the start of the 'data' chunk PCM samples in a WAV byte array."
  [^bytes wav]
  (loop [i 12]
    (when (< (+ i 8) (alength wav))
      (if (= (read-chunk-id wav i) "data")
        (+ i 8)
        (recur (+ i 8 (read-u32-le wav (+ i 4))))))))

(defn- sample-at
  "Read a 16-bit signed LE sample from a byte array at sample index."
  [^bytes pcm i]
  (let [offset (* i 2)
        lo (bit-and (aget pcm offset) 0xFF)
        hi (aget pcm (+ offset 1))]
    (short (bit-or lo (bit-shift-left hi 8)))))

(defn- trim-pcm
  "Trim leading and trailing silence from 16-bit PCM data.
   threshold is the absolute sample value below which is considered silence."
  [^bytes pcm threshold]
  (let [num-samples (quot (alength pcm) 2)
        ;; Find first non-silent sample
        start (loop [i 0]
                (cond
                  (>= i num-samples) i
                  (> (Math/abs (int (sample-at pcm i))) threshold) i
                  :else (recur (inc i))))
        ;; Find last non-silent sample
        end (loop [i (dec num-samples)]
              (cond
                (< i start) start
                (> (Math/abs (int (sample-at pcm i))) threshold) (inc i)
                :else (recur (dec i))))]
    (if (>= start end)
      (byte-array 0)
      (java.util.Arrays/copyOfRange pcm (int (* start 2)) (int (* end 2))))))

(defn- write-sample
  "Write a 16-bit signed LE sample to a byte array at sample index."
  [^bytes pcm i value]
  (let [offset (* i 2)
        clamped (max -32768 (min 32767 value))]
    (aset-byte pcm offset (unchecked-byte (bit-and clamped 0xFF)))
    (aset-byte pcm (+ offset 1) (unchecked-byte (bit-and (bit-shift-right clamped 8) 0xFF)))))

(defn- peak-level
  "Find the peak absolute sample value in 16-bit PCM data."
  [^bytes pcm]
  (let [num-samples (quot (alength pcm) 2)]
    (loop [i 0 peak 0]
      (if (>= i num-samples)
        peak
        (recur (inc i) (max peak (Math/abs (int (sample-at pcm i)))))))))

(defn- normalize-pcm
  "Normalize 16-bit PCM data to a target peak level (0.0-1.0 of max)."
  [^bytes pcm target]
  (let [num-samples (quot (alength pcm) 2)
        peak (peak-level pcm)]
    (if (or (zero? peak) (zero? num-samples))
      pcm
      (let [gain (/ (* target 32767.0) peak)
            result (byte-array (alength pcm))]
        (dotimes [i num-samples]
          (write-sample result i (int (* gain (sample-at pcm i)))))
        result))))

(defn- compress-pcm
  "Apply simple soft-knee compression to 16-bit PCM data.
   threshold: 0.0-1.0, level above which compression kicks in
   ratio: compression ratio (e.g. 3.0 means 3:1)"
  [^bytes pcm threshold ratio]
  (let [num-samples (quot (alength pcm) 2)
        thresh-val (* threshold 32767.0)
        result (byte-array (alength pcm))]
    (dotimes [i num-samples]
      (let [sample (double (sample-at pcm i))
            sign (if (neg? sample) -1.0 1.0)
            abs-sample (Math/abs sample)]
        (if (<= abs-sample thresh-val)
          (write-sample result i (int sample))
          (let [excess (- abs-sample thresh-val)
                compressed (+ thresh-val (/ excess ratio))
                out (* sign (min compressed 32767.0))]
            (write-sample result i (int out))))))
    result))

(defn- process-pcm
  "Apply compression then normalization to 16-bit PCM data."
  [^bytes pcm]
  (-> pcm
      (compress-pcm 0.5 3.0)
      (normalize-pcm 0.85)))

(defn- concat-wavs
  "Concatenate multiple WAV byte arrays into a single WAV."
  [wav-list]
  (if (= 1 (count wav-list))
    (first wav-list)
    (let [first-wav (first wav-list)
          header-size (wav-data-offset first-wav)
          pcm-chunks (mapv (fn [^bytes wav]
                             (let [offset (int (wav-data-offset wav))]
                               (java.util.Arrays/copyOfRange wav offset (int (alength wav)))))
                           wav-list)
          total-pcm (reduce + (map count pcm-chunks))
          result (byte-array (+ header-size total-pcm))]
      ;; Copy header from first WAV
      (System/arraycopy first-wav 0 result 0 header-size)
      ;; Update RIFF size (file size - 8)
      (let [riff-size (- (+ header-size total-pcm) 8)]
        (aset-byte result 4 (unchecked-byte (bit-and riff-size 0xFF)))
        (aset-byte result 5 (unchecked-byte (bit-and (bit-shift-right riff-size 8) 0xFF)))
        (aset-byte result 6 (unchecked-byte (bit-and (bit-shift-right riff-size 16) 0xFF)))
        (aset-byte result 7 (unchecked-byte (bit-and (bit-shift-right riff-size 24) 0xFF))))
      ;; Update data chunk size
      (let [data-size-offset (- header-size 4)]
        (aset-byte result data-size-offset (unchecked-byte (bit-and total-pcm 0xFF)))
        (aset-byte result (+ data-size-offset 1) (unchecked-byte (bit-and (bit-shift-right total-pcm 8) 0xFF)))
        (aset-byte result (+ data-size-offset 2) (unchecked-byte (bit-and (bit-shift-right total-pcm 16) 0xFF)))
        (aset-byte result (+ data-size-offset 3) (unchecked-byte (bit-and (bit-shift-right total-pcm 24) 0xFF))))
      ;; Copy PCM data
      (loop [chunks pcm-chunks offset header-size]
        (when (seq chunks)
          (let [^bytes chunk (first chunks)]
            (System/arraycopy chunk 0 result offset (alength chunk))
            (recur (rest chunks) (+ offset (alength chunk))))))
      result)))

(defn play-wav
  "Play WAV bytes through the default audio device."
  [wav-bytes]
  (let [tmp (java.io.File/createTempFile "bb-tts-" ".wav")]
    (.deleteOnExit tmp)
    (io/copy wav-bytes tmp)
    @(proc/process ["paplay" (str tmp)])
    (.delete tmp)))

(defn- silence-bytes
  "Generate raw PCM silence (16-bit, 44100Hz mono) for given seconds."
  [seconds]
  (byte-array (int (* 44100 2 seconds))))

(defn- seg->wav
  "Convert a segment to WAV bytes. Pauses become raw PCM silence."
  [seg]
  (if (= :pause (:type seg))
    (silence-bytes (:seconds seg))
    (synthesize seg)))

(defn perform
  "Synthesize all segments, concatenate, then play as one audio clip."
  [& segments]
  (ensure-server)
  (let [segs (flatten segments)
        ;; Synthesize speech segments, generate silence for pauses
        wavs (mapv (fn [seg]
                     (when (and *debug* (:text seg))
                       (binding [*out* *err*]
                         (println (format "[%s] %s" (:voice seg) (:text seg)))))
                     (if (= :pause (:type seg))
                       (silence-bytes (:seconds seg))
                       (synthesize seg)))
                   segs)
        ;; Separate: first real WAV (for header), then collect all PCM
        first-wav-idx (first (keep-indexed (fn [i seg] (when-not (= :pause (:type seg)) i)) segs))
        first-wav (nth wavs first-wav-idx)
        header-size (wav-data-offset first-wav)
        gap (silence-bytes 0.15)
        trimmed (mapv (fn [wav seg]
                        (if (= :pause (:type seg))
                          wav ;; already raw PCM silence
                          (let [offset (int (wav-data-offset wav))
                                raw (java.util.Arrays/copyOfRange ^bytes wav offset (int (alength ^bytes wav)))]
                            (-> raw (trim-pcm 200) process-pcm))))
                      wavs segs)
        ;; Insert gap between each chunk (not before first or after last)
        pcm-chunks (vec (interpose gap trimmed))
        total-pcm (reduce + (map count pcm-chunks))
        result (byte-array (+ header-size total-pcm))]
    ;; Build combined WAV
    (System/arraycopy first-wav 0 result 0 header-size)
    (let [riff-size (- (+ header-size total-pcm) 8)]
      (aset-byte result 4 (unchecked-byte (bit-and riff-size 0xFF)))
      (aset-byte result 5 (unchecked-byte (bit-and (bit-shift-right riff-size 8) 0xFF)))
      (aset-byte result 6 (unchecked-byte (bit-and (bit-shift-right riff-size 16) 0xFF)))
      (aset-byte result 7 (unchecked-byte (bit-and (bit-shift-right riff-size 24) 0xFF))))
    (let [data-size-offset (- header-size 4)]
      (aset-byte result data-size-offset (unchecked-byte (bit-and total-pcm 0xFF)))
      (aset-byte result (+ data-size-offset 1) (unchecked-byte (bit-and (bit-shift-right total-pcm 8) 0xFF)))
      (aset-byte result (+ data-size-offset 2) (unchecked-byte (bit-and (bit-shift-right total-pcm 16) 0xFF)))
      (aset-byte result (+ data-size-offset 3) (unchecked-byte (bit-and (bit-shift-right total-pcm 24) 0xFF))))
    (loop [chunks pcm-chunks offset header-size]
      (when (seq chunks)
        (let [^bytes chunk (first chunks)]
          (System/arraycopy chunk 0 result offset (alength chunk))
          (recur (rest chunks) (+ offset (alength chunk))))))
    (play-wav result)))
