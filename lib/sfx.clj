;; sfx.clj — Sound effects generator using sox.
;;
;; Generates fire ambiance and bird song as WAV files.
;; Requires sox on PATH.

(ns sfx
  (:require [babashka.process :as proc]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:dynamic *rate* 44100)
(def ^:dynamic *channels* 2)

(defn- rand-float [min max]
  (+ min (* (rand) (- max min))))

(defn- tmp [name]
  (str (System/getProperty "java.io.tmpdir") "/sfx_" name ".wav"))

(def sox-path
  (or (some #(when (.exists (io/file %)) %)
            ["/usr/bin/sox" "/run/current-system/sw/bin/sox"])
      (first (filter #(.exists (io/file %))
                     (map #(str % "/bin/sox")
                          (filter #(re-find #"sox" %)
                                  (map str (.listFiles (io/file "/nix/store")))))))
      "sox"))

(defn- sox [& args]
  (let [result @(proc/process (map str (flatten [sox-path args]))
                              {:out :inherit :err (io/file "/dev/null")})]
    (when-not (zero? (:exit result))
      (binding [*out* *err*]
        (println "sox warning: non-zero exit" (:exit result))))))

(defn- sox-info-duration [path]
  (Double/parseDouble
    (str/trim (:out @(proc/process ["sox" "--info" "-D" path] {:out :string})))))

;; --- Telephone ---

(defn generate-ringtone
  "Generate a telephone ringtone WAV (US style: 440+480 Hz, 2s on / 4s off).
   rings = number of rings."
  [rings output-file]
  (let [r (str *rate*)
        ring-files (mapv (fn [i]
                           (let [f (tmp (str "ring_" i))]
                             ;; 2s dual-tone ring
                             (sox "-n" "-r" r "-c" "1" f
                                  "synth" "2" "sine" "440" "sine" "480"
                                  "gain" "-6"
                                  "pad" "0" "3")
                             f))
                         (range rings))]
    ;; Concatenate rings
    (apply sox (concat ring-files [output-file]))
    (doseq [f ring-files] (.delete (io/file f)))
    output-file))

;; --- Modem ---
;; FT8-style frequency-hopping modulated tones, panned left/right
;; as a sub-vocal data exchange between two channels.

(def ^:private ft8-freqs
  "Discrete frequency slots in the 80-300 Hz band."
  (mapv #(+ 80 (* % 4.6)) (range 48)))

(defn- generate-ft8-transmission
  "Generate a single FT8-like transmission: a sequence of frequency-hopping
   tones, each ~0.16s, creating a modulated warble."
  [index]
  (let [r (str *rate*)
        ;; Each transmission is 8-20 tone slots
        slot-count (+ 8 (rand-int 13))
        slot-dur 0.16
        total-dur (* slot-count slot-dur)
        ;; Pick a base frequency band and hop around it
        base-idx (+ 5 (rand-int 35))
        slot-files (mapv (fn [s]
                           (let [sf (tmp (str "ft8_slot_" index "_" s))
                                 ;; Hop within ±6 slots of base
                                 freq (nth ft8-freqs
                                        (max 0 (min 47 (+ base-idx (- (rand-int 13) 6)))))]
                             (sox "-n" "-r" r "-c" "1" sf
                                  "synth" (str slot-dur) "sine" (str freq)
                                  "fade" "0.005" (str slot-dur) "0.005")
                             sf))
                         (range slot-count))
        f (tmp (str "modem_el_" index))]
    ;; Concatenate slots into one transmission, apply lowpass + envelope
    (apply sox (concat slot-files [f "lowpass" "350" "gain" "-14"
                                   "fade" "0.03" (str total-dur) "0.03"]))
    (doseq [s (range slot-count)]
      (.delete (io/file (tmp (str "ft8_slot_" index "_" s)))))
    f))

(defn generate-modem
  "Generate an FT8-style exchange track with transmissions panned left and right,
   as if two sides are communicating via frequency-hopping modulation."
  [duration output-file & {:keys [element-count gap-min gap-max]
                           :or {element-count 40 gap-min 0.3 gap-max 2.5}}]
  (let [r (str *rate*)
        parts (atom [])]
    (doseq [i (range element-count)]
      (let [el-file (generate-ft8-transmission i)
            gap-dur (rand-float gap-min gap-max)
            gap-file (tmp (str "modem_gap_" i))
            panned-file (tmp (str "modem_pan_" i))
            ;; Alternate left/right with some variation
            pan (if (even? i) (rand-float 0.7 0.95) (rand-float 0.05 0.3))
            l (str "1v" pan)
            rv (str "1v" (- 1.0 pan))]
        ;; Pan to stereo
        (sox el-file panned-file "remix" l rv)
        ;; Silence gap
        (sox "-n" "-r" r "-c" "2" gap-file "trim" "0" (str gap-dur))
        (swap! parts conj panned-file)
        (swap! parts conj gap-file)))
    ;; Concatenate all elements
    (apply sox (concat @parts [(tmp "modem_raw")]))
    ;; Trim to duration, filter and push back into the distance
    (sox (tmp "modem_raw") output-file "trim" "0" (str duration)
         "lowpass" "350" "lowpass" "350"
         "reverb" "50" "80" "90" "40"
         "gain" "-12" "norm" "-12")
    ;; Cleanup
    (doseq [i (range element-count)]
      (.delete (io/file (tmp (str "modem_el_" i))))
      (.delete (io/file (tmp (str "modem_gap_" i))))
      (.delete (io/file (tmp (str "modem_pan_" i)))))
    (.delete (io/file (tmp "modem_raw")))
    output-file))

;; --- Fire ---

(defn generate-fire
  "Generate a fire ambiance WAV file.
   Options: :body-gain :flame-gain :crackle-gain :pitch"
  [duration output-file & {:keys [body-gain flame-gain crackle-gain pitch]
                           :or {body-gain -14 flame-gain -18 crackle-gain -34 pitch -250}}]
  (let [dur (str duration)
        r (str *rate*)
        c (str *channels*)]

    ;; Brown noise — low body/rumble
    (sox "-n" "-r" r "-c" "1" (tmp "fire_body")
         "synth" dur "brownnoise"
         "highpass" "35" "lowpass" "420" "tremolo" "0.35" "22"
         "gain" (str body-gain))

    ;; Pink noise — mid flame movement
    (sox "-n" "-r" r "-c" "1" (tmp "fire_flame")
         "synth" dur "pinknoise"
         "bandpass" "650" "550" "tremolo" "2.7" "30"
         "gain" (str flame-gain))

    ;; White noise — high crackle/snap texture
    (sox "-n" "-r" r "-c" "1" (tmp "fire_crackle")
         "synth" dur "whitenoise"
         "highpass" "1800" "lowpass" "8500" "tremolo" "17" "80"
         "overdrive" "8" "gain" (str crackle-gain))

    ;; Mix the three layers with processing
    (sox "-m" (tmp "fire_body") (tmp "fire_flame") (tmp "fire_crackle")
         (tmp "fire_raw")
         "gain" "-8"
         "highpass" "30" "lowpass" "7000"
         "compand" "0.3,1" "6:-70,-58,-30" "-12" "-90" "0.2"
         "bass" "+6" "120"
         "pitch" (str pitch)
         "reverb" "70" "70" "90" "80" "20" "4"
         "gain" "-3")

    ;; Convert to stereo with slight spread
    (sox (tmp "fire_raw") output-file
         "remix" "1v0.55" "1v0.45"
         "norm")

    ;; Cleanup
    (doseq [s ["fire_body" "fire_flame" "fire_crackle" "fire_raw"]]
      (.delete (io/file (tmp s))))

    output-file))

;; --- Birds ---

(defn- generate-bird-syllable
  "Generate a single bird syllable WAV."
  [output-file base-freq]
  (let [dur (rand-float 0.10 0.28)
        vib-rate (rand-float 14 32)
        vib-depth (rand-float 6 18)
        gain (rand-float -42 -31)
        f (* base-freq (+ 0.75 (* (rand) 0.75)))
        f2 (* f 1.5)]
    (sox "-n" "-r" (str *rate*) "-c" "1"
         output-file
         "synth" (str dur) "sine" (str f) "sine" (str f2)
         "fade" "h" "0.008" (str dur) "0.045"
         "highpass" "1300" "lowpass" "7200"
         "tremolo" (str vib-rate) (str vib-depth)
         "chorus" "0.45" "0.65" "24" "0.22" "0.35" "3" "-t"
         "gain" (str gain))))

(defn- generate-bird-phrase
  "Generate a bird phrase (series of syllables with pauses)."
  [output-file]
  (let [syllable-count (+ 3 (rand-int 6))
        base-freq (rand-float 1700 3300)
        parts (atom [])]
    (doseq [i (range syllable-count)]
      (let [syl-file (tmp (str "bird_syl_" i))
            pause-dur (rand-float 0.035 0.18)
            pause-file (tmp (str "bird_pause_" i))]
        (generate-bird-syllable syl-file base-freq)
        (sox "-n" "-r" (str *rate*) "-c" "1" pause-file "trim" "0" (str pause-dur))
        (swap! parts conj syl-file)
        (swap! parts conj pause-file)))
    ;; Concatenate all syllables and pauses
    (apply sox (concat @parts [output-file]))
    ;; Convert to stereo with slight offset for space
    (let [stereo-file (tmp "bird_stereo")]
      (sox output-file stereo-file
           "remix" "1" "1"
           "delay" "0" "0.018"
           "reverb" "10" "25" "65" "30" "5" "0")
      (sox stereo-file output-file)
      (.delete (io/file stereo-file)))
    ;; Cleanup syllable files
    (doseq [i (range syllable-count)]
      (.delete (io/file (tmp (str "bird_syl_" i))))
      (.delete (io/file (tmp (str "bird_pause_" i)))))
    output-file))

(defn generate-birds
  "Generate a bird song track — random phrases with rests between them.
   Options: :rest-min :rest-max :phrase-count"
  [duration output-file & {:keys [rest-min rest-max phrase-count]
                           :or {rest-min 5 rest-max 18 phrase-count 12}}]
  (let [parts (atom [])]
    (doseq [i (range phrase-count)]
      ;; Rest before phrase
      (let [rest-dur (rand-float rest-min rest-max)
            rest-file (tmp (str "bird_rest_" i))
            phrase-file (tmp (str "bird_phrase_" i))]
        (sox "-n" "-r" (str *rate*) "-c" "2" rest-file "trim" "0" (str rest-dur))
        (generate-bird-phrase phrase-file)
        (swap! parts conj rest-file)
        (swap! parts conj phrase-file)))
    ;; Concatenate all phrases
    (apply sox (concat @parts [(tmp "birds_raw")]))
    ;; Trim to duration
    (sox (tmp "birds_raw") output-file "trim" "0" (str duration) "norm")
    ;; Cleanup
    (doseq [i (range phrase-count)]
      (.delete (io/file (tmp (str "bird_rest_" i))))
      (.delete (io/file (tmp (str "bird_phrase_" i)))))
    (.delete (io/file (tmp "birds_raw")))
    output-file))

;; --- Drone ---

(defn generate-drone
  "Generate a low drone."
  [duration output-file & {:keys [freq1 freq2 tremolo-rate pan]
                           :or {freq1 65 freq2 98 tremolo-rate 0.8 pan 0.5}}]
  (sox "-n" "-r" (str *rate*) "-c" "1" (tmp "drone_mono")
       "synth" (str duration) "sine" (str freq1) "sine" (str freq2)
       "tremolo" (str tremolo-rate) "reverb" "80" "norm")
  (let [l (str "1v" pan)
        r (str "1v" (- 1.0 pan))]
    (sox (tmp "drone_mono") output-file "remix" l r))
  (.delete (io/file (tmp "drone_mono")))
  output-file)

;; --- Drums ---

(defn generate-drums
  "Generate a drum loop."
  [duration output-file]
  (sox "-n" "-r" (str *rate*) "-c" "1" (tmp "dk") "synth" "0.15" "sine" "50:30" "fade" "0" "0.15" "0.1" "norm")
  (sox "-n" "-r" (str *rate*) "-c" "1" (tmp "dt") "synth" "0.12" "sine" "90:60" "fade" "0" "0.12" "0.08" "norm")
  (sox "-n" "-r" (str *rate*) "-c" "1" (tmp "dg1") "trim" "0" "0.35")
  (sox "-n" "-r" (str *rate*) "-c" "1" (tmp "dg2") "trim" "0" "0.48")
  (sox (tmp "dk") (tmp "dg1") (tmp "dg2") (tmp "dt") (tmp "dg1") (tmp "dk") (tmp "dg2") (tmp "dbar"))
  (let [bar-dur (sox-info-duration (tmp "dbar"))
        repeats (max 0 (dec (int (Math/ceil (/ duration bar-dur)))))]
    (sox (tmp "dbar") (tmp "drums_raw") "repeat" (str repeats))
    (sox (tmp "drums_raw") output-file "trim" "0" (str duration) "reverb" "50"
         "remix" "1v0.5" "1v0.5"))
  (doseq [s ["dk" "dt" "dg1" "dg2" "dbar" "drums_raw"]]
    (.delete (io/file (tmp s))))
  output-file)

;; --- Mixer ---

(defn mix
  "Mix multiple WAV files with individual gains into a single output.
   layers is a vector of [file gain-db] pairs.
   :gain sets final output gain in dB after normalization (e.g. -6 for half volume)."
  [layers output-file & {:keys [fade-in fade-out duration gain]
                         :or {fade-in 2 fade-out 3 gain 0}}]
  ;; Pre-apply gain to each layer
  (let [gained-files (mapv (fn [[file g]]
                             (let [out (tmp (str "mix_g_" (hash file)))]
                               (sox file out "gain" (str g))
                               out))
                           layers)
        dur-args (if duration [(str duration)] [])]
    (apply sox "-m" (concat gained-files
                            [output-file "fade" (str fade-in)]
                            dur-args
                            [(str fade-out) "gain" (str gain)]))
    ;; Cleanup gained files
    (doseq [f gained-files] (.delete (io/file f))))
  output-file)
