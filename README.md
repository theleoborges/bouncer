# bouncer

A tiny Clojure library for validating maps (or records).

## Table of Contents

* [Motivation](#motivation)
* [Setup](#setup)
* [Usage](#usage)
    * [Basic validations](#basic-validations)
    * [Validating nested maps](#validating-nested-maps)
    * [Multiple validation errors](#multiple-validation-errors)
    * [Validating collections](#validating-collections)
* [Composability: validator sets](#composability-validator-sets)
* [Customization](#customization)
    * [Custom validators using arbitrary functions](#custom-validations-using-arbitrary-functions)
    * [Writing validators](#writing-validators)
* [Built-in validators](#built-in-validations)
* [Contributing](#contributing)
* [TODO](#todo)
* [CHANGELOG](#changelog)
* [License](#license)

## Motivation

Check [this blog post](http://www.leonardoborges.com/writings/2013/01/04/bouncer-validation-lib-for-clojure/) where I explain in detail the motivation behind this library

## Setup

If you're using leiningen, add it as a dependency to your project:

```clojure
[bouncer "0.2.0"]
```

Or if you're using maven: 

```xml
<dependency>
  <groupId>bouncer</groupId>
  <artifactId>bouncer</artifactId>
  <version>0.2.0</version>
</dependency>
```

Then, require the library:

```clojure
(require '[bouncer [core :as b] [validators :as v]])
```

*bouncer* provides two main macros, `validate` and `valid?`

`valid?` is a convenience function built on top of `validate`:

```clojure
(b/valid? {:name nil}
    :name v/required)

;; false
```

`validate` takes a map and one or more validation forms and returns a vector. 

The first element in this vector contains a map of the error messages, whereas the second element contains the original map, augmented with the error messages.

Let's look at a few examples:

## Usage

### Basic validations

Below is an example where we're validating that a given map has a value for both the keys `:name` and `:age`.


```clojure
(require '[bouncer [core :as b] [validators :as v]])

(def person {:name "Leo"})

(b/validate person
    :name v/required
    :age  v/required)

;; [{:age ("age must be present")} 
;;  {:name "Leo", :errors {:age ("age must be present")}}]
```

As you can see, since age is missing, it's listed in the errors map with the appropriate error messages.

Error messages can be customized by providing a `:message` option - e.g: in case you need them internationalized: 

```clojure
(b/validate person
    :age (v/required :message "Idade é um atributo obrigatório"))

;; [{:age ("Idade é um atributo obrigatório")} 
;;  {:name "Leo", :errors {:age ("Idade é um atributo obrigatório")}}]
```

### Validating nested maps

Nested maps can easily be validated as well, using the built-in validators:

```clojure
(def person-1 
    {:address 
        {:street nil 
         :country "Brazil"
         :postcode "invalid"}})

(b/validate person-1
    [:address :street]   v/required
    [:address :postcode] v/number)

;; [{:address {:postcode ("postcode must be a number"), :street ("street must be present")}} 
;;  {:errors {:address {:postcode ("postcode must be a number"), :street ("street must be present")}},
;;   :address {:country "Brazil", :postcode "invalid", :street nil}}]
```

In the example above, the vector of keys is assumed to be the path in an associative structure.

### Multiple validation errors

If any of the entries fails more than one validation, all error messages are returned in a list like so:

```clojure
(b/validate person-1
    [:address :street] v/required
    [:address :postcode] [v/number v/positive])

;;[{:address {:postcode ("postcode must be a positive number" "postcode must be a number"), 
;;  :street ("street must be present")}} 
;;
;;  {:errors {:address {:postcode ("postcode must be a positive number" "postcode must be a number"), ;;   :street ("street must be present")}}, 
;;     :address {:country "Brazil", :postcode "invalid", :street nil}}]
```
The error map now contains the path `[:address :postcode]`, which is a list with all validation errors for that entry.

Also note that if we need multiple validations against any keyword or path, we need only provide them inside a vector, like `[v/number v/positive]` above.

### Validating collections

Sometimes it's useful to perform simple, ad-hoc checks in collections contained within a map. For that purpose, *bouncer* provides `every`. 

Its usage is similar to the validators seen so far. This time however, the value in the given key/path must be a collection (vector, list etc...)

Let's see it in action:

```clojure
(def person-with-pets {:name "Leo" 
                       :pets [{:name nil}
                              {:name "Gandalf"}]})

(b/validate person-with-pets
          :pets (v/every #(not (nil? (:name %)))))

;;[{:pets ("All items in pets must satisfy the predicate")} 
;; {:name "Leo", :pets [{:name nil} {:name "Gandalf"}], 
;; :errors {:pets ("All items in pets must satisfy the predicate")}}]
```

All we need to do is provide a predicate function to `every`. It will be invoked for every item in the collection, making sure they all pass.

## Composability: validator sets

If you find yourself repeating a set of validators over and over, chances are you will want to encapsulate that somehow. The macro `bouncer.validators/defvalidatorset` does just that:

```clojure
(use '[bouncer.validators :only [defvalidatorset]])

;; first we define the set of validators we want to use
(defvalidatorset addr-validator-set
  :postcode [v/required v/number]
  :street    v/required
  :country   v/required)

;;just something to validate
(def person {:address {
                :postcode ""
                :country "Brazil"}})

;;now we compose the validators
(b/validate person
            :name    v/required
            :address addr-validator-set)

;;[{:address 
;;    {:postcode ("postcode must be a number" "postcode must be present"), 
;;     :street ("street must be present")}, 
;;     :name ("name must be present")} 
;; 
;; {:errors {:address {:postcode ("postcode must be a number" "postcode must be present"), 
;;  :street ("street must be present")}, :name ("name must be present")}, 
;;  :address {:country "Brazil", :postcode ""}}]
```

## Customization

### Custom validations using arbitrary functions

Much like the collections validations above, *bouncer* gives you the ability to use arbitrary functions as predicates for validations through the `custom` built-in validator. Its usage should be familiar:

```clojure
(defn young? [age]
    (< age 25))

(b/validate {:age 29}
          :age (v/custom young? :message "Too old!"))

;; [{:age ("Too old!")} 
;;  {:errors {:age ("Too old!")}, :age 29}]
```

### Writing validators

Another way - and the preferred one - to provide custom validations is to use the macro `defvalidator` in the `bouncer.validators` namespace. 

The advantage of this approach is that your validator can be used in the same way built-in validators are - there's no need to use `bouncer.validators/custom`.

As an example, here's a simplified version of the `bouncer.validators/number` validator:

```clojure
(use '[bouncer.validators :only [defvalidator]])

(defvalidator my-number-validator
  {:default-message-format "%s must be a number"}
  [maybe-a-number]
  (number? maybe-a-number))
```

`defvalidator` takes your validator name, an optional map of options and the body of your predicate function.

Options is a map of key/value pairs where:

- `:default-message-format` - to be used when clients of this validator don't provide one
- `:optional` - a boolean indicating if this validator should only trigger for keys that have a value different than `nil`. Defaults to false.


Using it is then straightforward:

```clojure
(b/validate {:postcode "NaN"}
          :postcode my-number-validator)


;; [{:postcode ("postcode must be a number")} 
;;  {:errors {:postcode ("postcode must be a number")}, :postcode "NaN"}]
```

As you'd expect, the message can be customized as well:

```clojure
(b/validate {:postcode "NaN"}
          :postcode (my-number-validator :message "must be a number"))
```

## Built-in validations

I didn't spend a whole lot of time on *bouncer* so it only ships with the validations I've needed myself. At the moment they live in the validators namespace:

- `bouncer.validators/required`

- `bouncer.validators/number`

- `bouncer.validators/positive`

- `bouncer.validators/custom` (for ad-hoc validations)

- `bouncer.validators/every` (for ad-hoc validation of collections. All items must match the provided predicate)

## Contributing

Pull requests of bug fixes and new validators are most welcome.

Note that if you wish your validator to be merged and considered *built-in* you must implement it using the macro `defvalidator` shown above.

Feedback to both this library and this guide is welcome.

## TODO

- Allow `defvalidatorset` to encapsulate top level validator sets - including nested sets
- Add more validators (help is appreciated here)

## CHANGELOG

Check the CHANGELOG.md file in the project root

## License

Copyright © 2012 [Leonardo Borges](http://www.leonardoborges.com)

Distributed under the [MIT License](http://opensource.org/licenses/MIT).
