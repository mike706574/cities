# cities

[Lost Cities](https://en.wikipedia.org/wiki/Lost_Cities) in Clojure/ClojureScript.

Hosted at https://codenames-cities.herokuapp.com - I'm using the free tier, so it might take a bit to load the first time.

## Development

Start the frontend:

```
npx shadow-cljs watch frontend
```

Start the backend without a REPL:

```
lein run
```

Start the backend with a REPL:

```
lein repl
(load-file "dev/user.clj")
(reset)
```
