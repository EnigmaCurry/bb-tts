#!/usr/bin/env bb

(require '[babashka.http-client :as http]
         '[cheshire.core :as json]
         '[babashka.process :as proc]
         '[clojure.java.io :as io]
         '[babashka.cli :as cli])

(def default-opts
  {:host "127.0.0.1"
   :port 7788
   :voice "M1"
   :lang "en"
   :speed 1.0
   :format "wav"})

;; Force HTTP/1.1 — uvicorn drops the request body on HTTP/2 upgrade
(def http-client (http/client {:version :http1.1}))

(defn base-url [{:keys [host port]}]
  (str "http://" host ":" port))

(defn health [opts]
  (try
    (let [resp (http/get (str (base-url opts) "/v1/health")
                         {:client http-client})]
      (json/parse-string (:body resp) true))
    (catch Exception _e nil)))

(defn list-voices [opts]
  (let [resp (http/get (str (base-url opts) "/v1/styles")
                       {:client http-client})
        body (json/parse-string (:body resp) true)]
    (:styles body)))

(defn speak
  "Synthesize text and play it through the default audio device."
  [text opts]
  (let [payload (json/generate-string
                  {:text text
                   :voice (:voice opts)
                   :lang (:lang opts)
                   :speed (:speed opts)
                   :response_format (:format opts)})
        resp (http/post (str (base-url opts) "/v1/tts")
                        {:client http-client
                         :headers {"content-type" "application/json"}
                         :body payload
                         :as :bytes})
        tmp (java.io.File/createTempFile "bb-tts-" ".wav")]
    (.deleteOnExit tmp)
    (io/copy (:body resp) tmp)
    @(proc/process ["paplay" (str tmp)])
    (.delete tmp)))

(defn print-voices [opts]
  (let [voices (list-voices opts)]
    (println "Available voices:")
    (doseq [{:keys [name kind]} voices]
      (println (str "  " name " (" kind ")")))))

(defn print-help []
  (println "bb-tts - Text-to-speech via Supertonic")
  (println)
  (println "Usage: bb-tts.clj <command> [options]")
  (println)
  (println "Commands:")
  (println "  say <text>     Synthesize and play text")
  (println "  voices         List available voices")
  (println "  health         Check server status")
  (println)
  (println "Options:")
  (println "  --voice NAME   Voice style (default: M1)")
  (println "  --lang CODE    Language code (default: en)")
  (println "  --speed N      Speed 0.7-2.0 (default: 1.0)")
  (println "  --host HOST    Server host (default: 127.0.0.1)")
  (println "  --port PORT    Server port (default: 7788)"))

(let [{:keys [args opts]} (cli/parse-args *command-line-args*
                            {:coerce {:port :int
                                      :speed :double}})
      opts (merge default-opts opts)
      [cmd & rest-args] args]
  (case cmd
    "say"     (if (seq rest-args)
                (speak (clojure.string/join " " rest-args) opts)
                (do (println "Error: no text provided")
                    (System/exit 1)))
    "voices"  (print-voices opts)
    "health"  (if-let [h (health opts)]
                (println (json/generate-string h {:pretty true}))
                (do (println "Server not reachable")
                    (System/exit 1)))
    (print-help)))
