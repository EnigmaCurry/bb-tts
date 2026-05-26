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

;; Expression tags
(defn laugh  [] "<laugh>")
(defn breath [] "<breath>")
(defn sigh   [] "<sigh>")
(defn pause  [seconds] (str "<silence " seconds ">"))

;; Voice constructors — each returns a function that builds segments
(defn- make-voice [name]
  (fn [& parts]
    {:voice name
     :lang  *lang*
     :speed *speed*
     :text  (clojure.string/join " " (map #(if (fn? %) (%) %) parts))}))

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
  "Build a segment. First arg can be a voice fn or uses *voice*."
  [voice-or-text & parts]
  (if (fn? voice-or-text)
    (apply voice-or-text parts)
    {:voice *voice*
     :lang  *lang*
     :speed *speed*
     :text  (clojure.string/join " " (map #(if (fn? %) (%) %)
                                          (cons voice-or-text parts)))}))

(defn with-voice [voice & segments]
  (binding [*voice* voice]
    (vec (flatten segments))))

(defn with-speed [speed & segments]
  (binding [*speed* speed]
    (vec (flatten segments))))

(defn with-lang [lang & segments]
  (binding [*lang* lang]
    (vec (flatten segments))))

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

(defn play-wav
  "Play WAV bytes through the default audio device."
  [wav-bytes]
  (let [tmp (java.io.File/createTempFile "bb-tts-" ".wav")]
    (.deleteOnExit tmp)
    (io/copy wav-bytes tmp)
    @(proc/process ["paplay" (str tmp)])
    (.delete tmp)))

(defn perform
  "Synthesize and play a sequence of segments."
  [& segments]
  (ensure-server)
  (let [segs (flatten segments)]
    (doseq [seg segs]
      (when *debug*
        (binding [*out* *err*]
          (println (format "[%s] %s" (:voice seg) (:text seg)))))
      (play-wav (synthesize seg)))))
