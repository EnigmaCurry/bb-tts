#!/usr/bin/env bb

;; Pure glossolalia ritual — two voices, M5 and F5.
;; Oscillates between rapid dialog, repetition, and monologue.

(require '[tts :refer [perform say pause
                       M5 F5 with-speed]])

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
  (let [n (inc (rand-int 3))]
    (apply str (repeatedly n syllable))))

(defn phrase [n]
  (clojure.string/join " " (repeatedly n gword)))

;; --- Section generators ---

(defn rapid-dialog
  "Quick back-and-forth, short phrases."
  []
  (let [pairs (+ 4 (rand-int 4))]
    (with-speed 1.15
      (mapcat (fn [_]
                [(say M5 (phrase (+ 1 (rand-int 2))))
                 (say F5 (phrase (+ 1 (rand-int 2))))])
              (range pairs)))))

(defn echo-section
  "One voice says a phrase, the other repeats it."
  []
  (let [phrases (repeatedly (+ 3 (rand-int 3)) #(phrase (+ 2 (rand-int 2))))
        leader (if (< (rand) 0.5) [M5 F5] [F5 M5])]
    (with-speed 0.85
      (mapcat (fn [p]
                [(say (first leader) p)
                 (say (second leader) p)])
              phrases))))

(defn monologue
  "One voice speaks alone, longer phrases."
  []
  (let [voice (rand-nth [M5 F5])
        lines (+ 4 (rand-int 4))]
    (with-speed 0.8
      (mapv (fn [_] (say voice (phrase (+ 3 (rand-int 3))))) (range lines)))))

(defn unison
  "Both voices say the same thing."
  []
  (let [p (phrase 3)]
    [(say M5 p)
     (say F5 p)]))

;; --- Build the ritual ---

(def sections
  [rapid-dialog echo-section monologue rapid-dialog
   echo-section monologue rapid-dialog unison])

(println "=== The Ritual ===\n")

(doseq [[i section-fn] (map-indexed vector sections)]
  (let [segs (section-fn)]
    (apply perform (concat segs [(pause 0.8)]))))

;; Final unison chant
(let [w (gword)]
  (perform
    (with-speed 0.7
      (say M5 (str w ". " w ". " w "."))
      (say F5 (str w ". " w ". " w "."))
      (pause 0.5)
      (say M5 w)
      (say F5 w))))
