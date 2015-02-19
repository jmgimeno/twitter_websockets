(ns twitterclient.twitterclient
  (:require [clojure.string :as str]
            [twitterclient.http :as http-client]
            [twitterclient.processing :as processing]
            [clojure.core.async :as async :refer [pipe]]
            [clj-time.core :as t]
            [clojure.core.async :as async :refer [chan]]))

(defn start-twitter-api [tweets]
  (let [last-received (atom (t/epoch))
        chunk-chan (chan 1 (processing/process-chunk last-received) processing/ex-handler)
        conn (atom {})
        ;watch-active (atom false)
        ]
    (http-client/start-twitter-conn! conn chunk-chan)
    (pipe chunk-chan tweets false)))