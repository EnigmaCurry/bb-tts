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
;; Reverb settings: [reverberance hf-damping room-scale stereo-depth]
;; nil = no reverb. E.g. [30 50 80 40] for light room.
(def ^:dynamic *reverb* nil)

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

;; --- Pronunciation ---

(def ^:dynamic *pronunciations* {})

(defn- apply-pronunciations [text]
  (reduce (fn [s [from to]]
            (clojure.string/replace s (re-pattern (str "(?i)\\b" (java.util.regex.Pattern/quote from) "\\b")) to))
          text *pronunciations*))

;; --- Synthesis and playback ---

(defn synthesize
  "Send text to the TTS server, return WAV bytes."
  [{:keys [text voice lang speed]}]
  (let [text (apply-pronunciations text)
        payload (json/generate-string
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

(defn- normalize-pcm
  "Normalize 16-bit PCM to target peak (0.0-1.0) using ByteBuffer for speed."
  [^bytes pcm target]
  (let [buf (doto (java.nio.ByteBuffer/wrap pcm)
              (.order java.nio.ByteOrder/LITTLE_ENDIAN))
        num-samples (quot (alength pcm) 2)
        ;; Pass 1: find peak
        peak (loop [i 0 p (int 0)]
               (if (>= i num-samples)
                 p
                 (recur (inc i) (max p (Math/abs (int (.getShort buf (* i 2))))))))]
    (if (zero? peak)
      pcm
      (let [gain (/ (* target 32767.0) (double peak))
            result (byte-array (alength pcm))
            out (doto (java.nio.ByteBuffer/wrap result)
                  (.order java.nio.ByteOrder/LITTLE_ENDIAN))]
        ;; Pass 2: scale
        (dotimes [i num-samples]
          (let [sample (.getShort buf (* i 2))
                scaled (max -32768 (min 32767 (int (* gain sample))))]
            (.putShort out (* i 2) (short scaled))))
        result))))

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
  "Generate raw PCM stereo silence (16-bit, 44100Hz) for given seconds."
  [seconds]
  (byte-array (int (* 44100 4 seconds))))

(defn- mono->stereo
  "Convert mono 16-bit PCM to stereo with panning.
   pan: -1.0 (full left) to 1.0 (full right), 0 = center."
  [^bytes mono-pcm pan]
  (let [num-samples (quot (alength mono-pcm) 2)
        stereo (byte-array (* num-samples 4))
        buf-in (doto (java.nio.ByteBuffer/wrap mono-pcm)
                 (.order java.nio.ByteOrder/LITTLE_ENDIAN))
        buf-out (doto (java.nio.ByteBuffer/wrap stereo)
                  (.order java.nio.ByteOrder/LITTLE_ENDIAN))
        ;; Equal-power panning
        angle (* (+ pan 1.0) 0.25 Math/PI)
        gain-l (Math/cos angle)
        gain-r (Math/sin angle)]
    (dotimes [i num-samples]
      (let [sample (.getShort buf-in (* i 2))
            left (max -32768 (min 32767 (int (* gain-l sample))))
            right (max -32768 (min 32767 (int (* gain-r sample))))]
        (.putShort buf-out (* i 4) (short left))
        (.putShort buf-out (+ (* i 4) 2) (short right))))
    stereo))

(defn- seg->wav
  "Convert a segment to WAV bytes. Pauses become raw PCM silence."
  [seg]
  (if (= :pause (:type seg))
    (silence-bytes (:seconds seg))
    (synthesize seg)))

(defn- expression-tag? [seg]
  (and (:text seg) (clojure.string/starts-with? (:text seg) "<")))

(defn- synthesize-seg
  "Synthesize a segment and return stereo PCM bytes (no WAV header).
   Respects :pan key (-1.0 left, 0 center, 1.0 right)."
  [seg]
  (if (= :pause (:type seg))
    (silence-bytes (:seconds seg))
    (let [wav (synthesize seg)
          offset (int (wav-data-offset wav))
          raw (java.util.Arrays/copyOfRange ^bytes wav offset (int (alength ^bytes wav)))
          trimmed (trim-pcm raw 200)
          processed (if (expression-tag? seg)
                      trimmed
                      (normalize-pcm trimmed 0.95))
          pan (or (:pan seg) 0.0)]
      (mono->stereo processed pan))))

(defn- synthesize-all
  "Synthesize all segments into a sequence of stereo PCM byte arrays with gaps."
  [segments]
  (let [segs (vec (flatten segments))
        gap (silence-bytes 0.15)]
    (mapcat (fn [[i seg]]
              (when (and *debug* (:text seg))
                (binding [*out* *err*]
                  (println (format "[%s] %s" (:voice seg) (:text seg)))))
              (if (zero? i)
                [(synthesize-seg seg)]
                [gap (synthesize-seg seg)]))
            (map-indexed vector segs))))

(defn- write-wav-file
  "Write stereo 16-bit 44100Hz PCM chunks to a WAV file."
  [chunks output-file]
  (let [pcm-data (mapv identity chunks)
        total-pcm (reduce + (map count pcm-data))
        header-size 44
        result (byte-array (+ header-size total-pcm))
        buf (doto (java.nio.ByteBuffer/wrap result)
              (.order java.nio.ByteOrder/LITTLE_ENDIAN))]
    ;; RIFF header
    (.put buf (.getBytes "RIFF"))
    (.putInt buf (- (+ header-size total-pcm) 8))
    (.put buf (.getBytes "WAVE"))
    ;; fmt chunk — stereo 16-bit 44100Hz
    (.put buf (.getBytes "fmt "))
    (.putInt buf 16)          ;; chunk size
    (.putShort buf (short 1)) ;; PCM format
    (.putShort buf (short 2)) ;; channels
    (.putInt buf 44100)       ;; sample rate
    (.putInt buf 176400)      ;; byte rate (44100 * 2 * 2)
    (.putShort buf (short 4)) ;; block align (channels * bytes per sample)
    (.putShort buf (short 16));; bits per sample
    ;; data chunk
    (.put buf (.getBytes "data"))
    (.putInt buf total-pcm)
    ;; PCM data
    (doseq [^bytes chunk pcm-data]
      (.put buf chunk))
    (io/copy result (io/file output-file))
    output-file))

(defn render
  "Synthesize all segments and write to a WAV file.
   Optionally applies reverb if *reverb* is set."
  [output-file & segments]
  (ensure-server)
  (let [chunks (synthesize-all segments)
        raw-file (str output-file ".raw")]
    (if *reverb*
      (let [[rev hf room stereo] *reverb*
            ;; Write raw PCM, process with sox for reverb, output WAV
            tmp-raw (java.io.File/createTempFile "bb-tts-render-" ".raw")]
        (.deleteOnExit tmp-raw)
        (with-open [out (io/output-stream tmp-raw)]
          (doseq [^bytes chunk chunks]
            (.write out chunk)))
        @(proc/process ["sox"
                        "-t" "raw" "-r" "44100" "-c" "2" "-e" "signed" "-b" "16" (str tmp-raw)
                        output-file
                        "reverb" (str rev) (str hf) (str room) (str stereo)]
                       {:out :inherit :err (io/file "/dev/null")})
        (.delete tmp-raw))
      (write-wav-file chunks output-file))
    output-file))

(defn perform
  "Synthesize segments with streaming playback — starts playing as soon
   as the first segment is ready while continuing to render ahead."
  [& segments]
  (ensure-server)
  (let [segs (vec (flatten segments))
        gap (silence-bytes 0.15)
        queue (java.util.concurrent.LinkedBlockingQueue.)
        ;; Producer: synthesize segments and push PCM chunks to queue
        producer (future
                   (try
                     (doseq [[i seg] (map-indexed vector segs)]
                       (when (and *debug* (:text seg))
                         (binding [*out* *err*]
                           (println (format "[%s] %s" (:voice seg) (:text seg)))))
                       (when (pos? i) (.put queue gap))
                       (.put queue (synthesize-seg seg)))
                     (finally
                       (.put queue ::done))))
        ;; Start playback pipeline — optionally through sox for reverb
        player (if *reverb*
                 (let [[rev hf room stereo] *reverb*
                       sox-proc (proc/process
                                  ["sox" "-t" "raw" "-r" "44100" "-c" "2" "-e" "signed" "-b" "16" "-"
                                   "-t" "raw" "-r" "44100" "-c" "2" "-e" "signed" "-b" "16" "-"
                                   "reverb" (str rev) (str hf) (str room) (str stereo)]
                                  {:in :pipe :out :pipe :err (io/file "/dev/null")})
                       paplay (proc/process
                                ["paplay" "--raw" "--format=s16le" "--rate=44100" "--channels=2"]
                                {:in (:out sox-proc) :out :inherit :err :inherit})]
                   {:proc (:proc sox-proc) :paplay paplay :sox sox-proc})
                 (proc/process ["paplay" "--raw" "--format=s16le"
                                "--rate=44100" "--channels=2"]
                               {:in :pipe :out :inherit :err :inherit}))
        out (if *reverb*
              (.getOutputStream (:proc (:sox player)))
              (.getOutputStream (:proc player)))]
    ;; Consumer: write PCM chunks to paplay as they arrive
    (try
      (loop []
        (let [chunk (.take queue)]
          (when-not (= ::done chunk)
            (.write out ^bytes chunk)
            (.flush out)
            (recur))))
      (finally
        (.close out)
        (if *reverb*
          (do @(:sox player) @(:paplay player))
          @player)
        @producer))))
