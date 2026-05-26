#!/usr/bin/env bb

;; Mystical sorcery incantations — dark rituals and arcane chants.

(require '[tts :refer [perform say pause breath sigh
                       M1 M2 M3 M4 M5 F1 F2 F3 F4 F5
                       with-speed]])

(def ancient-words
  ["azaroth" "velkonis" "shemara" "drakonis" "nethûl" "volmaris"
   "khethani" "zul'varak" "morthis" "ashkari" "grimaldi" "sorvanus"
   "ul'thane" "vexorim" "narthul" "obsidrane" "kholmari" "zhavros"
   "tel'gorath" "umbrani" "sylvaris" "dûmkrest" "pharonis" "ignathar"
   "val'morien" "eskandros" "revathi" "krynn'os" "thal'mera" "zur'akhi"])

(def elements
  ["shadow" "flame" "void" "stone" "ash" "frost" "storm"
   "bone" "iron" "blood" "thorn" "dust" "smoke" "crystal"
   "obsidian" "mercury" "sulfur" "salt" "amber" "onyx"])

(def vessels
  ["chalice" "crucible" "sigil" "altar" "pentacle" "phylactery"
   "athame" "censer" "obelisk" "monolith" "grimoire" "reliquary"
   "cauldron" "brazier" "sarcophagus" "rune stone" "scrying pool"
   "black mirror" "hourglass" "astrolabe"])

(def actions
  ["bind" "summon" "banish" "awaken" "consume" "shatter"
   "invoke" "unravel" "devour" "transmute" "consecrate" "desecrate"
   "seal" "unleash" "corrupt" "purify" "ignite" "extinguish"
   "rend" "weave" "forge" "dissolve" "conjure" "entomb"])

(def entities
  ["the hollow king" "the serpent of ages" "the nameless one"
   "the watcher between" "the pale architect" "the formless hunger"
   "the bone weaver" "the dream eater" "the star drinker"
   "the ash mother" "the void herald" "the thorn sovereign"
   "the silent choir" "the iron oracle" "the last witness"
   "the deep crawling chaos" "the obsidian shepherd" "the flame tongue"])

(def domains
  ["the space between stars" "the marrow of the earth"
   "the river of forgotten names" "the halls of unmade things"
   "the well of black glass" "the roots beneath the world"
   "the door that was never opened" "the silence after thunder"
   "the wound in the sky" "the garden of teeth"
   "the library of ashes" "the throne of echoes"
   "the cradle of extinction" "the labyrinth of veins"
   "the furnace of dead suns" "the altar of unbecoming"])

(def refrains
  ["So it was spoken. So it shall be."
   "The circle is drawn. The price is paid."
   "From nothing, something. From something, nothing."
   "What sleeps shall wake. What wakes shall hunger."
   "The old words hold. The old words bind."
   "Ash to ash. Void to void."
   "The seal is broken. There is no return."
   "The stars remember what the earth forgets."])

(defn pick [coll] (rand-nth coll))

(defn invocation-line []
  (rand-nth
    [(str (pick ancient-words) ", " (pick ancient-words) ", " (pick ancient-words))
     (str "By the " (pick elements) " and the " (pick elements) ", I " (pick actions) " thee")
     (str "I call upon " (pick entities) " who dwells in " (pick domains))
     (str "Let the " (pick vessels) " of " (pick elements) " " (pick actions) " " (pick entities))
     (str "Through " (pick domains) ", the " (pick elements) " " (pick vessels) " shall " (pick actions))
     (str (pick ancient-words) "! " (pick ancient-words) "! Rise from " (pick domains))]))

(defn make-incantation []
  (vec (repeatedly 6 invocation-line)))

(def sorcerers
  [{:name "The Hollow Magus"      :voice M3  :speed 0.85}
   {:name "The Ash Priestess"     :voice F2  :speed 0.8}
   {:name "The Bone Scrivener"    :voice M5  :speed 0.9}
   {:name "The Void Sibyl"        :voice F4  :speed 0.75}
   {:name "The Iron Hierophant"   :voice M1  :speed 0.85}])

(println "=== The Ritual of Unbecoming ===\n")

;; Opening
(perform
  (with-speed 0.75
    (say F2 (breath) "We gather at the threshold."))
  (pause 1.0))

(doseq [{:keys [name voice speed]} sorcerers]
  (println (str "[" name "]"))
  (let [lines (make-incantation)]
    (doseq [line lines] (println (str "  " line)))
    (println)
    (perform
      (with-speed speed
        (say voice (breath) (first lines))
        (apply say voice (interpose "." (rest lines))))
      (pause 0.5)
      (with-speed 0.75
        (say voice (pick refrains)))
      (pause 1.0))))

;; Closing ritual — all voices in unison
(println "[The Circle]")
(let [final-refrain (pick refrains)]
  (println (str "  " final-refrain))
  (println)
  (perform
    (pause 0.5)
    (with-speed 0.7
      (say M3 (str (pick ancient-words) ". " (pick ancient-words) ". " (pick ancient-words) "."))
      (say F2 (sigh) (str "The " (pick vessels) " is " (pick actions) "ed."))
      (say M5 (str (pick entities) " " (pick actions) "s " (pick domains) "."))
      (say F4 (breath) final-refrain)
      (pause 1.0)
      (say M1 "It is done."))))
