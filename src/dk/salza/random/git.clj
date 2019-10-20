(ns dk.salza.random.git
  "Evaluate this content to get some level of git support in Liquid.
  It REQUIRES git to be installed as a command line tools beforehand to work
  It will simply run git commands in the folder of the active file.
  At the buttom of the file the key-bindings are defined.
  To test open a file in a git repo and type:
  space g l
  The result of the git log command will be shown, if there is any."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [dk.salza.liq.editor :as editor]
            [dk.salza.liq.apps.promptapp :as promptapp])
  (:import [java.lang ProcessBuilder]
           [java.util.concurrent TimeUnit]))


(defn- split-arguments
  "Splits arguments/vector into list of parameters
  and a map, if there are keywords.
  (split-arguments [1 2 3 :timeout 10]) -> [[1 2 3] {:timeout 10}]"
  [args]
  (apply #(vector %1 (apply hash-map %2)) (split-with #(not (keyword? %)) args)))

(defn- take-realized
  [coll]
  (if-not (instance? clojure.lang.IPending coll)
    (cons (first coll) (take-realized (rest coll)))
    (when (realized? coll)
      (cons (first coll) (take-realized (rest coll))))))

(defn cmd
  "Execute a native command.
  Adding :timeout 60 or similar as last command will
  add a timeout to the process."
  [& args]
  (let [[parameters options] (split-arguments args)
        builder (doto (ProcessBuilder. parameters)
                  (.redirectErrorStream true))
        process (.start builder)]
    (if (if (options :timeout)
          (.waitFor process (options :timeout) TimeUnit/SECONDS)
          (.waitFor process))
      (str/join "\n" (doall (line-seq (io/reader (.getInputStream process)))))
      (str (str/join "\n" (take-realized (line-seq (io/reader (.getInputStream process)))))
           "\nTimeoutException"))))

(defn get-git-parent
  [filepath]
  (let [f (io/file filepath)]
    (cond (.isFile f) (get-git-parent (.getParent f))
          (some #{".git"} (for [subf (.listFiles f)] (.getName subf))) (str f)
          (= (str f) "/") nil
          :else (get-git-parent (.getParent f)))))


(defn status
  [filepath]
  (when filepath
    (when-let [gitdir (get-git-parent filepath)]
      (str (cmd "git" "--work-tree" gitdir "--git-dir" (str gitdir "/.git") "status" "-s")))))

(defn add
  [filepath]
  (when filepath
    (when-let [gitdir (get-git-parent filepath)]
      (str (cmd "git" "--work-tree" gitdir "--git-dir" (str gitdir "/.git") "add" filepath)))))

(defn add-all
  [filepath]
  (when filepath
    (when-let [gitdir (get-git-parent filepath)]
      (str (cmd "git" "--work-tree" gitdir "--git-dir" (str gitdir "/.git") "add" "--all")))))


(defn commit
  [filepath message]
  (when filepath
    (when-let [gitdir (get-git-parent filepath)]
      (str (cmd "git" "--work-tree" gitdir "--git-dir" (str gitdir "/.git") "commit" "-a" "-m" message)))))

(defn push
  [filepath]
  (when filepath
    (when-let [gitdir (get-git-parent filepath)]
      (str (cmd "git" "--work-tree" gitdir "--git-dir" (str gitdir "/.git") "push")))))

(defn log
  [filepath]
  (when filepath
    (when-let [gitdir (get-git-parent filepath)]
      (str (cmd "git" "--work-tree" gitdir "--git-dir" (str gitdir "/.git") "log" filepath)))))

(defn load-git
  []
  (editor/set-spacekey ["g"] "Git" nil)
  (editor/set-spacekey ["g" "s"] "Git status" #(editor/prompt-set (status (editor/get-folder))))
  (editor/set-spacekey ["g" "a"] "git add" #(editor/prompt-set (add (editor/get-filename))))
  (editor/set-spacekey ["g" "f"] "git add --all" #(editor/prompt-set (add-all (editor/get-filename))))
  (editor/set-spacekey ["g" "c"] "git commit"
    #(promptapp/run (fn [m] (editor/prompt-set (commit (editor/get-folder) m))) ["Message"]))
  (editor/set-spacekey ["g" "p"] "git push" #(editor/prompt-set (push (editor/get-folder))))
  (editor/set-spacekey ["g" "l"] "git log" #(editor/prompt-set (log (editor/get-filename)))))

(load-git)