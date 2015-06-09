(ns twitter-websockets.server
  (:require [clojure.java.io :as io]
            [twitter-websockets.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [compojure.core :refer [GET POST defroutes routes]]
            [compojure.route :refer [resources not-found]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [ring.middleware.session :refer [wrap-session]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [taoensso.sente :as sente]
            [clojure.core.async :as async :refer [<! <!! chan go-loop thread]]
            [twitterclient.twitterclient :as tc]
            [clojure.data.priority-map :refer [priority-map-by]]
            [clojure.edn :as edn])
  (:import (java.util UUID)))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! {})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def connected-uids connected-uids)                       ; Watchable, read-only atom
  )

(def params (edn/read-string (slurp "application-params.edn")))


; UUID and session management

(defn unique-id
  "Return a really unique ID (for an unsecured session ID).
  No, a random number is not unique enough. Use a UUID for real!"
  []
  (.toString (UUID/randomUUID)))

(defn session-uid
  "Convenient to extract the UID that Sente needs from the request."
  [req]
  (get-in req [:session :uid]))

(deftemplate page
             (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn index
  "Handle index page request. Injects session uid if needed."
  [req]
  {:status  200
   :session (if (session-uid req)
              (:session req)
              (assoc (:session req) :uid (unique-id)))
   :body    (page)})

(defroutes my-routes
           (-> (routes
                 (resources "/")
                 (resources "/react" {:root "react"})

                 (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
                 (POST "/chsk" req (ring-ajax-post req))

                 (GET "/*" req (#'index req))

                 (not-found "These are not the androids that you're looking for."))
               wrap-session))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (api #'my-routes))
    (api my-routes)))

(defn run [& [port]]
  (defonce ^:private server
           (do
             (if is-dev? (start-figwheel))
             (let [port (Integer. (or port (env :port) 10555))]
               (print "Starting web server on port" port ".\n")
               (run-server http-handler {:port  port
                                         :join? false}))))
  server)

(def tweets-chan (chan))

(def num-langs-clients (atom {}))

(def lang-statistics (atom (priority-map-by >)))

(defn get-lang-statistics [langs-count num-langs]
  (let [[lang-statistics other] (split-at num-langs langs-count)]
    (conj lang-statistics (apply + (map second other)))))

(defn get-num-langs-uid [langs-clients uid]
  (get langs-clients uid (:num-lang-statistics params)))

(defn refresh-all-clients [tweet clock]
  (doseq [uid (:any @connected-uids)]
    #_(println "Sending to uid " uid)
    (chsk-send! uid
                [:tweets/text (:text tweet)])
    (if (zero? clock)
      (chsk-send! uid
                  [:tweets/lang (get-lang-statistics @lang-statistics
                                                     (get-num-langs-uid @num-langs-clients uid))])
      #_(println (get-lang-statistics updated-langs-count)))))

(defn update-language-statistics [lang]
  (swap! lang-statistics #(update-in % [lang] (fnil inc 0))))

(defn next-tick [clock]
  (-> clock inc (mod (:freq-lang-statistics params))))

(defn tweets-loop []
  (go-loop [clock 0]
    (let [tweet (<! tweets-chan)]
      (update-language-statistics (:lang tweet))
      (refresh-all-clients tweet clock)
      (recur (next-tick clock)))))

; Event handling

(defmulti handle-event (fn [[ev-id ev-data]] ev-id))

(defmethod handle-event :twitter_websockets/langs-count [[_ {:keys [nlangs uid]}]]
  #_(println "Received from: "  uid)
  (swap! num-langs-clients assoc uid (read-string nlangs))
  (chsk-send! uid [:tweets/lang (get-lang-statistics @lang-statistics
                                                         (get-num-langs-uid @num-langs-clients uid))]))

(defmethod handle-event :default [[ev-id ev-data :as event]]
  #_(println "Received:" event))

(defn event-loop []
  (go-loop []
    (let [{:keys [event]} (<! ch-chsk)]
      (thread (handle-event event)))
    (recur)))

(defn -main [& [port]]
  (run port)
  (tc/start-twitter-api tweets-chan)
  (tweets-loop)
  (event-loop)
  )
