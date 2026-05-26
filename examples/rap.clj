#!/usr/bin/env bb

;; Gnomish nonsense rap — crisp dream edition.

(require '[tts :refer [perform say pause breath laugh sigh
                       M1 M2 M3 M4 M5 F1 F2 F3 F4 F5
                       with-speed]])

(def rhyme-sets
  [["plonk" "conk" "honk" "bonk" "clonk" "stonk" "wonk" "zonk"]
   ["drizzle" "fizzle" "sizzle" "grizzle" "chisel" "weasel" "diesel" "easel"]
   ["crumple" "rumple" "stumple" "grumple" "dumple" "pumple" "lumple" "bumple"]
   ["flark" "snark" "blark" "grark" "twark" "splark" "demark" "klark"]
   ["bloop" "snoop" "droop" "swoop" "gloop" "stoop" "troop" "scoop"]
   ["crunble" "funble" "bunble" "stunble" "grunble" "trunble" "spunble" "dunble"]
   ["twiddle" "griddle" "spiddle" "criddle" "briddle" "friddle" "skriddle" "priddle"]
   ["glimber" "shimber" "timber" "slimber" "crimber" "whimber" "drimber" "frimber"]
   ["sproink" "ploink" "floink" "troink" "groink" "kroink" "bloink" "sploink"]
   ["wumble" "grumble" "fumble" "stumble" "crumble" "tumble" "mumble" "bumble"]])

(def adjectives
  ["crinkled" "plonkish" "frothing" "glinty" "smudged" "puckered"
   "wobbish" "dripsome" "crusty" "foggled" "blinky" "rumpled"
   "squidgy" "clammy" "frizzled" "gloopy" "withered" "spongiform"
   "craggled" "mottled" "bristly" "tufted" "soggy" "pebbled"
   "knobbled" "scrunchy" "chalky" "musty" "gnarled" "crispy"])

(def nouns
  ["grommet" "spigot" "turnstile" "bollard" "thimble" "gasket"
   "trinket" "sprocket" "waddle" "plunger" "bucket" "spool"
   "nozzle" "ratchet" "funnel" "toggle" "bobbin" "gimbal"
   "spindle" "cog" "flannel" "grout" "pumice" "rivet"
   "bung" "cleat" "dowel" "gusset" "ferrule" "widget"
   "corbel" "finial" "mullion" "newel" "plinth" "soffit"
   "noggin" "gudgeon" "pintle" "trunnel"])

(def verbs
  ["squelched" "plodded" "sputtered" "gurgled" "clattered" "skittered"
   "squidged" "trundled" "waddled" "puttered" "clomped" "shuffled"
   "dribbled" "fumbled" "floundered" "stammered" "gargled" "tottered"
   "flailed" "scuttled" "blundered" "groped" "shambled" "teetered"
   "burped" "hiccupped" "snorkeled" "splattered" "thwacked" "bonked"])

(def places
  ["through the damp crevice" "under the mossy overhang"
   "behind the dripping pipe" "inside the lint drawer"
   "across the gravel pit" "down the storm drain"
   "beneath the rusty grate" "atop the soggy mound"
   "within the cork cupboard" "past the leaky cistern"
   "around the wonky shelf" "beside the crusty faucet"
   "over the pebble ridge" "along the foggy ditch"
   "amid the dusty rafters" "near the creaky hinge"])

(def interjections
  ["Plonk!" "Crispy dreams!" "Squelch it!"
   "Toggle the sprocket!" "Great gussets!"
   "Fumble me a flannel!" "By the soggy mound!"
   "Spigots alive!" "Well grout my plinth!"
   "Sproink!" "Bung it!" "What a cog!"])

(defn pick [coll] (rand-nth coll))

(defn make-couplet []
  (let [rhymes (pick rhyme-sets)
        [r1 r2] (take 2 (shuffle rhymes))]
    [(str "The " (pick adjectives) " " (pick nouns) " " (pick verbs) " the " (pick nouns) " with a " r1)
     (str "And the " (pick adjectives) " " (pick nouns) " " (pick verbs) " " (pick places) " in a " r2)]))

(defn make-verse []
  (mapcat identity (repeatedly 4 make-couplet)))

(def bards
  [["The Grommet King" M3]
   ["Soggy Pete" M5]
   ["Lady Sprocket" F1]
   ["The Wobblish Sage" M1]
   ["Dame Plunger" F4]])

(println "=== The Grand Council of Crispy Dreams ===\n")
(doseq [[name voice] bards]
  (println (str "[" name "]"))
  (let [lines (make-verse)]
    (doseq [line lines] (println (str "  " line)))
    (println)
    (perform
      (say voice (pick interjections))
      (pause 0.2)
      (with-speed 1.05
        (apply say voice (interpose "." lines)))
      (pause 0.3)
      (say voice (pick interjections))
      (pause 0.5))))

(println "[Everyone]")
(perform
  (say M3 (pick interjections))
  (say F1 (breath) (str "The " (pick adjectives) " " (pick nouns) " has spoken!"))
  (say M5 (str (pick interjections) " " (pick interjections)))
  (say F4 (laugh) "And so concludes the grand council of crispy dreams!")
  (say M1 (sigh) "Squelch."))
