(ns twitter-websockets.server
  (:require [clojure.java.io :as io]
            [twitter-websockets.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources not-found]]
            [compojure.handler :refer [api]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [taoensso.sente :as sente]
            [clojure.core.async :as async :refer [<! <!! chan go-loop thread]])
  (:import (java.util UUID)))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
 )

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
  {:status 200
   :session (if (session-uid req)
              (:session req)
              (assoc (:session req) :uid (unique-id)))
   :body (page)})

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})

  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))

  (GET "/*" req (#'index req))

  (not-found "These are not the androids that you're looking for."))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (api #'routes))
    (api routes)))

(defn run [& [port]]
  (defonce ^:private server
    (do
      (if is-dev? (start-figwheel))
      (let [port (Integer. (or port (env :port) 10555))]
        (print "Starting web server on port" port ".\n")
        (run-server http-handler {:port port
                          :join? false}))))
  server)

(defn start-broadcaster! []
  (go-loop [i 0]
    (<! (async/timeout 10000))
    (println (format "Broadcasting server>user: %s" @connected-uids))
    (doseq [uid (:any @connected-uids)]
      (println "Sending to uid " uid)
      (chsk-send! uid
        [:some/broadcast
         {:what-is-this "A broadcast pushed from server"
          :how-often    "Every 10 seconds"
          :to-whom uid
          :i i}]))
    (recur (inc i))))

(defn -main [& [port]]
  (run port)
  (start-broadcaster!))
