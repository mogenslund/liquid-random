(ns dk.salza.random.socket-repl
  "To use: See comment below this ns declaration."
  (:require [clojure.string :as str]
            [dk.salza.liq.editor :as editor])
  (:import [java.net Socket]
           [java.io BufferedInputStream BufferedOutputStream BufferedReader
                    InputStreamReader]
           [java.nio.charset Charset]))

(comment
  ; To run execute one of the commands below in a terminal:
  ; clj -J-Dclojure.server.repl="{:port 5555 :accept clojure.core.server/repl}"
  ; lumo -n 5555
  ; Then evaluate this file (c p f)
  ; Now send form to repl or define keybindings.
  (socket-jack-in "localhost" 5555)
  (send-to-repl "(+ 1 2 3)")
  (editor/add-keybinding "dk.salza.liq.keymappings.normal" "U" (fn [] (send-to-repl (str (or (editor/get-selection) (editor/sexp-at-point)))) nil))
  (+ 1 2 3)
  (send-to-repl "(def abc :def)")
  (socket-jack-out))
  

(def socket (atom nil))

(defn socket-jack-in
  [host port]
  (reset! socket (Socket. host port))
  (future
    (let [reader (BufferedReader. (InputStreamReader. (BufferedInputStream. (.getInputStream @socket))))]
      (loop []
        (when (and @socket (.isConnected @socket)) 
          (editor/prompt-append (.readLine reader))
          (recur))))))

(defn socket-jack-out
  []
  (when @socket
    (.close @socket)
    (reset! socket nil)))
    

(defn send-to-repl
  [code]
  (when @socket
    (let [out (BufferedOutputStream. (.getOutputStream @socket))]
      (.write out (.getBytes (str code "\n") (Charset/forName "UTF-8")))
      (.flush out))))

