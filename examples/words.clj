#!/usr/bin/env bb

;; Reads 100 random words from the dictionary and speaks them.

(require '[tts :refer [perform say M1 M2 M3 M4 M5 F1 F2 F3 F4 F5
                       ensure-server]]
         '[clojure.java.io :as io]
         '[babashka.process :as proc])

(def dict-paths
  ["/usr/share/dict/words"
   ;; scowl from nix
   (some #(when (.exists (io/file %)) %)
         (map #(str % "/share/scowl/english-words.20")
              (clojure.string/split (or (System/getenv "PATH") "") #":")))
   ;; try to find scowl in the nix store
   (first (filter #(.exists (io/file %))
                  (map #(str % "/share/scowl/english-words.20")
                       (clojure.string/split (or (System/getenv "XDG_DATA_DIRS") "") #":"))))])

(defn find-dict []
  (or (first (filter some? (map #(when (and % (.exists (io/file %))) %) dict-paths)))
      ;; Last resort: glob the nix store for scowl
      (first (filter #(.exists (io/file %))
                     (->> (.listFiles (io/file "/nix/store"))
                          (filter #(re-find #"scowl" (.getName %)))
                          (map #(str % "/share/scowl/english-words.20")))))
      (do (binding [*out* *err*]
            (println "No dictionary found. Run inside 'nix develop' or install scowl."))
          (System/exit 1))))

(def voices [["M1" M1] ["M2" M2] ["M3" M3] ["M4" M4] ["M5" M5]
             ["F1" F1] ["F2" F2] ["F3" F3] ["F4" F4] ["F5" F5]])

(let [words (->> (slurp (find-dict))
                 clojure.string/split-lines
                 (filter #(re-matches #"[a-z]{4,}" %))
                 shuffle
                 (take 100)
                 (partition-all 10))]
  (ensure-server)
  (doseq [[[voice-name voice] batch] (map vector voices words)]
    (println (str "--- " voice-name " ---"))
    (println (clojure.string/join ". " batch))
    (perform (apply say voice (interpose "." batch)))))
