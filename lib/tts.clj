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

;; Expression tags — return strings that get joined into segment text
(defn laugh  [] "<laugh>")
(defn breath [] "<breath>")
(defn sigh   [] "<sigh>")

;; Pause — returns a special segment with silence duration
(defn pause [seconds]
  {:type :pause :seconds seconds})

;; Voice constructors — each returns a function that builds segments.
;; Parts can be strings, expression tag fns, or pause segments.
;; Pauses split the text into separate speech segments with a pause between.
(defn- build-segments [voice lang speed parts]
  (let [resolved (map #(cond (fn? %)  (%)
                             (string? %) %
                             :else %)
                      parts)]
    (loop [remaining resolved
           current-text []
           result []]
      (if (empty? remaining)
        (if (seq current-text)
          (conj result {:voice voice :lang lang :speed speed
                        :text (clojure.string/join " " current-text)})
          result)
        (let [part (first remaining)]
          (if (and (map? part) (= :pause (:type part)))
            (let [segs (if (seq current-text)
                         (conj result {:voice voice :lang lang :speed speed
                                       :text (clojure.string/join " " current-text)})
                         result)]
              (recur (rest remaining) [] (conj segs part)))
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
        pcm-chunks (mapv (fn [wav seg]
                           (if (= :pause (:type seg))
                             wav ;; already raw PCM
                             (let [offset (int (wav-data-offset wav))]
                               (java.util.Arrays/copyOfRange ^bytes wav offset (int (alength ^bytes wav))))))
                         wavs segs)
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
