#!/usr/bin/env bb

;; Generates and speaks random mad-lib sentences.

(require '[tts :refer [perform say laugh breath sigh pause
                       M1 M2 M3 M4 M5 F1 F2 F3 F4 F5]])

(def nouns
  ["penguin" "robot" "astronaut" "wizard" "pancake" "dragon" "pirate"
   "hamster" "professor" "vampire" "detective" "mermaid" "goblin"
   "spaceship" "dinosaur" "toaster" "unicorn" "ninja" "banana"
   "octopus" "scarecrow" "submarine" "llama" "gargoyle" "potato"
   "walrus" "yeti" "flamingo" "cactus" "jellyfish" "mushroom"
   "kangaroo" "pickle" "hedgehog" "tornado" "lighthouse" "satellite"
   "trombone" "volcano" "caterpillar" "doorknob" "iceberg" "hammock"
   "platypus" "accordion" "bumblebee" "chandelier" "dumpling"
   "elephant" "ferret" "giraffe" "harmonica" "iguana" "jackal"])

(def verbs-past
  ["launched" "devoured" "befriended" "juggled" "serenaded" "discovered"
   "challenged" "hypnotized" "interrogated" "married" "outsmarted"
   "photographed" "rescued" "tackled" "unleashed" "vanquished"
   "wrestled" "ambushed" "bamboozled" "catapulted" "demolished"
   "enchanted" "flattened" "gobbled" "haunted" "impersonated"
   "kidnapped" "levitated" "mesmerized" "obliterated" "pickled"
   "questioned" "roasted" "swallowed" "tickled" "upstaged"
   "vaporized" "wrangled" "zapped" "adopted" "betrayed"
   "celebrated" "debugged" "entertained" "frightened" "grilled"
   "hugged" "insulted" "jinxed" "karate chopped" "lassoed"])

(def adjectives
  ["fluffy" "enormous" "invisible" "caffeinated" "suspicious" "tiny"
   "radioactive" "furious" "majestic" "bewildered" "flamboyant"
   "grumpy" "spectacular" "confused" "ferocious" "ridiculous"
   "magnificent" "peculiar" "terrifying" "delightful" "mischievous"
   "legendary" "outrageous" "preposterous" "questionable" "sarcastic"
   "thunderous" "unhinged" "volcanic" "whimsical" "zealous"
   "ancient" "baffled" "cranky" "dazzling" "eccentric" "flustered"
   "gargantuan" "hysterical" "irate" "jumpy" "klutzy" "luminous"
   "melodramatic" "nefarious" "obnoxious" "pompous" "quarrelsome"
   "rambunctious" "sneaky" "tremendous" "ungrateful" "vivacious"])

(def adverbs
  ["frantically" "elegantly" "suspiciously" "heroically" "accidentally"
   "dramatically" "enthusiastically" "furiously" "gracefully"
   "hilariously" "mysteriously" "nervously" "obnoxiously" "passionately"
   "quietly" "recklessly" "shamelessly" "triumphantly" "unexpectedly"
   "violently" "wildly" "zealously" "absurdly" "boldly" "clumsily"
   "defiantly" "excessively" "foolishly" "gleefully" "haphazardly"])

(def locations
  ["on the moon" "in a bathtub" "behind the dumpster" "at the opera"
   "inside a volcano" "on a roller coaster" "in the library"
   "under the bed" "at a fancy restaurant" "in a bouncy castle"
   "on a pirate ship" "in the middle of nowhere" "at the dentist"
   "inside a whale" "on top of a skyscraper" "in a phone booth"
   "at the north pole" "in a hot air balloon" "under a waterfall"
   "at a disco" "in a submarine" "on a trampoline" "in a cave"
   "at a rodeo" "in a treehouse" "on a gondola" "in a hammock"
   "at a laundromat" "in a corn maze" "on a unicycle"])

(def templates
  [;; Narrative
   (fn [] (format "The %s %s %s %s a %s %s %s, and then %s turned around and %s %s the %s %s who was watching from behind a %s %s."
            (rand-nth adjectives) (rand-nth nouns) (rand-nth adverbs)
            (rand-nth verbs-past) (rand-nth adjectives) (rand-nth nouns)
            (rand-nth locations) (rand-nth nouns) (rand-nth adverbs)
            (rand-nth verbs-past) (rand-nth adjectives) (rand-nth nouns)
            (rand-nth adjectives) (rand-nth nouns)))
   ;; Chain of events
   (fn [] (format "A %s %s was minding its own business %s, when suddenly a %s %s appeared and %s %s it. Not to be outdone, a %s %s %s %s both of them while a %s %s watched %s."
            (rand-nth adjectives) (rand-nth nouns) (rand-nth locations)
            (rand-nth adjectives) (rand-nth nouns) (rand-nth adverbs)
            (rand-nth verbs-past) (rand-nth adjectives) (rand-nth nouns)
            (rand-nth adverbs) (rand-nth verbs-past) (rand-nth adjectives)
            (rand-nth nouns) (rand-nth adverbs)))
   ;; Two subjects with consequences
   (fn [] (format "The %s %s and the %s %s %s %s each other %s, which caused the %s %s to %s panic, and eventually the %s %s had to come and sort the whole mess out %s."
            (rand-nth adjectives) (rand-nth nouns) (rand-nth adjectives)
            (rand-nth nouns) (rand-nth adverbs) (rand-nth verbs-past)
            (rand-nth locations) (rand-nth adjectives) (rand-nth nouns)
            (rand-nth adverbs) (rand-nth adjectives) (rand-nth nouns)
            (rand-nth locations)))
   ;; Breaking news
   (fn [] (format "Breaking news! A %s %s has %s %s the %s %s %s. Witnesses say the %s %s then %s %s a nearby %s before fleeing %s on a %s %s."
            (rand-nth adjectives) (rand-nth nouns) (rand-nth adverbs)
            (rand-nth verbs-past) (rand-nth adjectives) (rand-nth nouns)
            (rand-nth locations) (rand-nth adjectives) (rand-nth nouns)
            (rand-nth adverbs) (rand-nth verbs-past) (rand-nth nouns)
            (rand-nth adverbs) (rand-nth adjectives) (rand-nth nouns)))
   ;; Once upon a time
   (fn [] (format "Once upon a time, a %s %s lived %s with a %s %s. One day, the %s %s %s the %s, and from that moment on, nothing %s was ever the same, especially not the %s %s who had %s %s the whole thing."
            (rand-nth adjectives) (rand-nth nouns) (rand-nth locations)
            (rand-nth adjectives) (rand-nth nouns) (rand-nth nouns)
            (rand-nth adverbs) (rand-nth verbs-past) (rand-nth nouns)
            (rand-nth locations) (rand-nth adjectives) (rand-nth nouns)
            (rand-nth adverbs) (rand-nth verbs-past)))])

(def voices [M1 M2 M3 M4 M5 F1 F2 F3 F4 F5])

(println "--- Mad Libs ---")
(doseq [i (range 20)]
  (let [sentence ((rand-nth templates))
        voice (rand-nth voices)]
    (println sentence)
    (perform (say voice sentence))))
