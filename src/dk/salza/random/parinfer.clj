(ns dk.salza.random.parinfer
  "This is a version of parinfer that uses the \"original\" JavaScript
  version of parinfer written by Shaun Lebron.
  It takes advantage of Nashorn to integrate with JavaScript.
  (This might be very usefull to other plugins as well.)

  TO USE:
  First, do a checkout of https://github.com/shaunlebron/parinfer
  or at least download the js file:
      https://github.com/shaunlebron/parinfer/tree/master/lib/parinfer.js
  Then include org.clojure/data.json {:mvn/version \"0.2.6\"} in the deps.edn file
  Update the parinfer-js-file variable to reference the parinfer.js file just
  downloaded or checked out.
  Evaluate this file and parinfer should be in use.
  To toggle parinfer press space twice and typeahead to
  \"Toggle Parinfer\" and press enter.
  
  Please use with caution. It is some kind of experimental."
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.pprint :as pprint]
            [dk.salza.liq.slider :refer :all]
            [dk.salza.liq.editor :as editor])
  (:import [javax.script ScriptEngineManager ScriptEngine]))

(def parinfer-js-file "/home/sosdamgx/proj/parinfer/lib/parinfer.js")
(def old-insertmode (atom nil))
(def nashorn (atom nil))

(defn load-parinfer-engine
  []
  (reset! nashorn (.getEngineByName (ScriptEngineManager.) "nashorn"))
  (.eval @nashorn (slurp parinfer-js-file)))

(defn pretty-format [m] 
  (let [w (java.io.StringWriter.)]
     (pprint/pprint m w) (.toString w)))

(defn beginning-of-toplevel
  [sl]
  (loop [s sl]
    (let [c (get-char s)
          cb (get-char (left s))]
      (if (or
            (beginning? s)
            (and (not= c " ") (not= c "\n") (= cb "\n")))
        s
        (recur (left s))))))

(defn end-of-toplevel
  [sl]
  (loop [s sl]
    (let [c (get-char s)
          cn (get-char (right s))]
      (if (or
            (end? s)
            (and (= c "\n") (not= cn "\n") (not= cn " ")))
        s
        (recur (right s))))))

(defn js-smart-mode
  [text options]
  (let [t (str/replace text  #"\n" "\\\\n")]
    (json/read-str (.eval @nashorn (str "JSON.stringify(parinfer.smartMode('" t "', " (json/write-str options) "));")) :key-fn keyword)))

(defn forward-lines
  [sl n]
  (last (take (+ n 1) (iterate forward-line sl))))
  
(defn smart-mode
  [sl fun]
  (try
    (let [sl1 (fun sl)
          p1 (get-point sl1)
          sl2 (-> sl1
                  end-of-toplevel
                  (set-mark "parinfer")
                  beginning-of-toplevel
                  left
                  beginning-of-toplevel)
          p2 (get-point sl2)
          delta (get-linenumber sl2)
          subsl (set-point (get-region-as-slider sl2 "parinfer") (- p1 p2))
          res (js-smart-mode (get-content subsl)
                             {:cursorLine (- (get-linenumber sl1) delta) 
                              :cursorX (- (get-point sl1) (get-point (beginning-of-line sl1))) 
                              :prevCursorLine (- (get-linenumber sl) delta)
                              :prevCursorX (- (get-point sl) (get-point (beginning-of-line sl)))})]
      (-> sl2
          (delete-region "parinfer")
          (insert (res :text))
          (set-point p2)
          (forward-lines (res :cursorLine))
          (right (res :cursorX))))
    (catch Exception e (fun sl))))
  
(defn parinfer-keymap
  []
  (assoc ((@editor/editor ::editor/keymaps) "dk.salza.liq.keymappings.insert")
     "backspace" (fn [] (editor/set-undo-point) (editor/apply-to-slider (fn [sl] (smart-mode sl #(delete % 1)))))
     " " (fn [] (editor/set-undo-point) (editor/apply-to-slider (fn [sl] (smart-mode sl #(insert % " ")))))
     :selfinsert (fn [string] (editor/apply-to-slider (fn [sl] (smart-mode sl #(insert % string)))))))

(defn toggle-parinfer
  []
  (let [m1 @old-insertmode
        m2 ((@editor/editor ::editor/keymaps) "dk.salza.liq.keymappings.insert")]
    (reset! old-insertmode m2)
    (editor/add-keymap m1)))

(defn load-parinfer
  []
  (load-parinfer-engine)
  (when (not @old-insertmode)
    (reset! old-insertmode (parinfer-keymap)))
  (toggle-parinfer)
  (editor/add-interactive "Toggle parinfer" toggle-parinfer))

(load-parinfer)