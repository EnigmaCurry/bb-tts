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
   :format "wav"
   :debug false})

(def debug? (atom false))

;; Force HTTP/1.1 — uvicorn drops the request body on HTTP/2 upgrade
(def http-client (http/client {:version :http1.1}))

(defn base-url [{:keys [host port]}]
  (str "http://" host ":" port))

(defn debug [& args]
  (when @debug?
    (binding [*out* *err*]
      (apply println args))))

(defmacro timed [label & body]
  `(let [start# (System/currentTimeMillis)
         result# (do ~@body)
         elapsed# (- (System/currentTimeMillis) start#)]
     (debug (format "%s: %dms" ~label elapsed#))
     result#))

(defn health [opts]
  (try
    (timed "Health check"
      (let [resp (http/get (str (base-url opts) "/v1/health")
                           {:client http-client})]
        (json/parse-string (:body resp) true)))
    (catch Exception _e nil)))

(defn ensure-server [opts]
  (when-not (health opts)
    (debug "Starting supertonic server...")
    (proc/process ["supertonic-serve"
                   "--host" (:host opts)
                   "--port" (str (:port opts))]
                  {:out (if @debug? :inherit "/dev/null")
                   :err (if @debug? :inherit "/dev/null")})
    (loop [attempts 60]
      (cond
        (health opts)   true
        (pos? attempts) (do (Thread/sleep 500) (recur (dec attempts)))
        :else           (do (binding [*out* *err*]
                              (println "Error: server failed to start"))
                            (System/exit 1))))))

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
        resp (timed "Synthesize"
               (http/post (str (base-url opts) "/v1/tts")
                          {:client http-client
                           :headers {"content-type" "application/json"}
                           :body payload
                           :as :bytes}))
        tmp (java.io.File/createTempFile "bb-tts-" ".wav")]
    (.deleteOnExit tmp)
    (io/copy (:body resp) tmp)
    (timed "Playback"
      @(proc/process ["paplay" (str tmp)]))
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
  (println "  --port PORT    Server port (default: 7788)")
  (println "  --debug        Show timing and server logs"))

(let [{:keys [args opts]} (cli/parse-args *command-line-args*
                            {:coerce {:port :int
                                      :speed :double
                                      :debug :boolean}})
      opts (merge default-opts opts)]
  (reset! debug? (:debug opts))
  (let [[cmd & rest-args] args]
    (case cmd
      "say"     (if (seq rest-args)
                  (do (ensure-server opts)
                      (speak (clojure.string/join " " rest-args) opts))
                  (do (println "Error: no text provided")
                      (System/exit 1)))
      "voices"  (do (ensure-server opts) (print-voices opts))
      "health"  (if-let [h (do (ensure-server opts) (health opts))]
                  (println (json/generate-string h {:pretty true}))
                  (do (println "Server not reachable")
                      (System/exit 1)))
      (print-help))))
