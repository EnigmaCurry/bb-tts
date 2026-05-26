#!/usr/bin/env bb

;; Reads 100 random words from the system dictionary and speaks them.

(require '[tts :refer [perform say M1 M2 M3 M4 M5 F1 F2 F3 F4 F5
                       ensure-server *server*]]
         '[clojure.java.io :as io]
         '[babashka.process :as proc])

(def dict-paths ["/usr/share/dict/words"
                 "/run/current-system/sw/share/dict/words"
                 "/nix/var/nix/profiles/default/share/dict/words"])

(defn find-dict []
  (or (first (filter #(.exists (io/file %)) dict-paths))
      (do (binding [*out* *err*]
            (println "No dictionary found. Install words package."))
          (System/exit 1))))

(def voices [M1 M2 M3 M4 M5 F1 F2 F3 F4 F5])

(let [words (->> (slurp (find-dict))
                 clojure.string/split-lines
                 (filter #(> (count %) 3))
                 shuffle
                 (take 100))
      voice-cycle (cycle voices)]
  (ensure-server)
  (doseq [[word voice] (map vector words voice-cycle)]
    (println word)
    (perform (say voice word))))
