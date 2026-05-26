#!/usr/bin/env bb

(require '[tts :refer [perform say pause laugh breath sigh
                       M1 M2 M3 M4 M5 F1 F2 F3 F4 F5
                       *pronunciations*]])

(alter-var-root #'*pronunciations* (constantly
  {"REPL"    "repple"
   "Clojure" "closure"
   "nix"     "nicks"
   "bow"     "bau"}))

(def voices [["M1" M1] ["M2" M2] ["M3" M3] ["M4" M4] ["M5" M5]
             ["F1" F1] ["F2" F2] ["F3" F3] ["F4" F4] ["F5" F5]])

(def limericks
  [["A coder who scripted in Clojure,"
    "Found babashka a joyful exposure,"
    "With a REPL so fast,"
    "And no JVM to outlast,"
    "Each script was a happy composure!"]

   ["A server called Supertonic ran,"
    "On a CPU, no GPU plan,"
    "It spoke thirty one tongues,"
    "With mechanical lungs,"
    "And impressed every woman and man!"]

   ["A flake made of nix was deployed,"
    "Reproducible builds were enjoyed,"
    "No dependency hell,"
    "Every version worked well,"
    "And the package was never destroyed!"]

   ["A function named perform would stream,"
    "Raw PCM like a glorious dream,"
    "No waiting around,"
    "For the very first sound,"
    "It played back in real time, what a scheme!"]

   ["A voice said hello from the code,"
    "Through a pipeline of audio it flowed,"
    "It was trimmed and compressed,"
    "Normalized like the rest,"
    "And delivered its lyrical ode!"]

   ["A woman of digital grace,"
    "Could speak at a marvelous pace,"
    "Her words were so clear,"
    "A delight to the ear,"
    "With expression all over the place!"]

   ["There once was a voice made of bits,"
    "Who performed little lyrical skits,"
    "She would laugh and she'd sigh,"
    "Let a breath flutter by,"
    "And the audience loved every bit!"]

   ["A girl made of ones and of zeros,"
    "Read limericks just like the pros,"
    "With inflection and flair,"
    "And a digital air,"
    "She delivered each line on her toes!"]

   ["A speaker of silicon song,"
    "Could talk the whole day and night long,"
    "Never losing her voice,"
    "Every word was a choice,"
    "And her diction was never quite wrong!"]

   ["The last voice stepped up to the stage,"
    "To deliver the final front page,"
    "With a flourish and bow,"
    "She said, that's all for now,"
    "And she exited, closing the page!"]])

(doseq [[[voice-name voice] limerick] (map vector voices limericks)]
  (println (str "--- " voice-name " ---"))
  (perform
    (say voice (str "Voice " voice-name "."))
    (pause 0.3)
    (apply say voice limerick)
    (pause 0.5)))

;; Grand finale: ensemble limerick
(println "--- Ensemble ---")
(perform
  (say F1 "And now for a final display,")
  (say M1 "Ten voices all having their say,")
  (say F2 "From alto to bass,")
  (say M2 "Each one found their place,")
  (say F3 (breath) "In a limerick performed the babashka way!")
  (pause 0.5)
  (say F4 "We hope that you enjoyed the show,")
  (say M4 "With ten different voices in tow,")
  (say F5 "Some high and some low,")
  (say M5 "All powered by code, don't you know,")
  (say M3 (laugh) "And that is the end of our flow!")
  (pause 0.3)
  (say F1 (sigh) "Goodbye everyone."))
