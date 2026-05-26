#!/usr/bin/env bb

;; Pure glossolalia ritual with fire, birds, drone, and drums.
;; Two voices (M5 and F5) panned left/right over a soundscape.

(require '[tts :refer [perform say pause M5 F5 with-speed ensure-server]]
         '[sfx :as sfx]
         '[babashka.process :as proc]
         '[clojure.java.io :as io])

;; --- Glossolalia generator ---

(def onsets
  ["z" "k" "th" "v" "sh" "d" "g" "b" "m" "kh"
   "f" "r" "p" "t" "s" "l" "n" "h" "w" "j"
   "ch" "dr" "gl" "br" "fr" "tr" "pr" "kr" "zh" "sk"])

(def vowels
  ["a" "e" "i" "o" "u" "ah" "oh" "ee" "oo" "ai"
   "ei" "au" "ay" "ey" "uh" "aa" "oi"])

(def endings
  ["" "" "" "" "" "" "n" "m" "r" "l" "s" "th" "sh"])

(defn syllable []
  (str (rand-nth onsets) (rand-nth vowels) (rand-nth endings)))

(defn gword []
  (apply str (repeatedly (inc (rand-int 3)) syllable)))

;; Thunderwords — massive compound nonsense words, Finnegans Wake style
(def thunder-onsets
  ["b" "d" "g" "kr" "th" "br" "dr" "gr" "tr" "sk" "str" "pr"])

(def thunder-vowels
  ["a" "o" "u" "oo" "ah" "ou" "au" "oh"])

(def thunder-clusters
  ["rr" "nn" "mm" "nk" "ng" "nth" "rn" "rm" "nd" "nt" "nnt" "rrh"
   "ghh" "kk" "tt" "pp" "nth" "wn" "rd" "rth" "ghr" "ght"])

(defn thunder-syllable []
  (str (rand-nth thunder-onsets)
       (rand-nth thunder-vowels)
       (rand-nth thunder-clusters)))

(defn thunderword []
  (let [n (+ 12 (rand-int 16))]
    (apply str (repeatedly n thunder-syllable))))

(defn phrase [n]
  (clojure.string/join " " (repeatedly n gword)))

(defn maybe-thunderword
  "With ~15% chance, insert a thunderword into a section."
  [voice-fn]
  (when (< (rand) 0.15)
    (with-speed 0.75
      (voice-fn (thunderword)))))

;; --- Speech sections with panning ---

(defn pan-segs [segs pan]
  (mapv #(if (map? %) (assoc % :pan pan) %) (flatten segs)))

(defn say-m [& parts] (pan-segs (apply say M5 parts) -0.5))
(defn say-f [& parts] (pan-segs (apply say F5 parts) 0.5))

(defn rapid-dialog []
  (concat
    (with-speed 1.15
      (mapcat (fn [_]
                (concat (say-m (phrase (+ 1 (rand-int 2))))
                        (say-f (phrase (+ 1 (rand-int 2))))))
              (range (+ 4 (rand-int 4)))))
    (maybe-thunderword (rand-nth [say-m say-f]))))

(defn echo-section []
  (let [phrases (repeatedly (+ 3 (rand-int 3)) #(phrase (+ 2 (rand-int 2))))
        [a b] (if (< (rand) 0.5) [say-m say-f] [say-f say-m])]
    (concat
      (with-speed 0.85
        (mapcat (fn [p] (concat (a p) (b p))) phrases))
      (maybe-thunderword (rand-nth [say-m say-f])))))

(defn monologue []
  (let [voice-fn (rand-nth [say-m say-f])]
    (concat
      (with-speed 0.8
        (vec (mapcat (fn [_] (voice-fn (phrase (+ 3 (rand-int 3)))))
                     (range (+ 4 (rand-int 4))))))
      (maybe-thunderword voice-fn))))

(defn unison []
  (let [p (phrase 3)]
    (concat (say-m p) (say-f p))))

;; --- Main ---

(ensure-server)

(def duration 150)
(def bg-file (str (System/getProperty "java.io.tmpdir") "/ritual_bg.wav"))

(println "Generating soundscape...")
(let [fire-file (sfx/generate-fire duration (sfx/tmp "fire_out"))
      birds-file (sfx/generate-birds duration (sfx/tmp "birds_out")
                   :rest-min 5 :rest-max 15 :phrase-count 15)
      drone-file (sfx/generate-drone duration (sfx/tmp "drone_out")
                   :pan 0.55)
      drums-file (sfx/generate-drums duration (sfx/tmp "drums_out"))]
  (sfx/mix [[fire-file -4]
            [birds-file -6]
            [drone-file -3]
            [drums-file -5]]
           bg-file
           :fade-in 3 :fade-out 5 :duration duration)
  ;; Cleanup individual layers
  (doseq [f [fire-file birds-file drone-file drums-file]]
    (.delete (io/file f))))

(println "=== The Ritual ===\n")

;; Start background
(def bg-player (proc/process ["paplay" bg-file]
                             {:out :inherit :err :inherit}))

;; Speech sections
(def sections
  [rapid-dialog echo-section monologue rapid-dialog
   echo-section monologue rapid-dialog echo-section unison])

(doseq [section-fn sections]
  (apply perform (concat (section-fn) [(pause 0.8)])))

;; Final chant
(let [w (gword)]
  (perform
    (with-speed 0.7
      (say-m (str w ". " w ". " w "."))
      (say-f (str w ". " w ". " w "."))
      (pause 0.5)
      (say-m w)
      (say-f w))))

;; Let background fade out
(future (Thread/sleep 5000) (.destroy (:proc bg-player)))
@bg-player
(.delete (io/file bg-file))
