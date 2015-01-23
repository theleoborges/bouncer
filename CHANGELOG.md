## 0.3.2 (23/01/2015)

- Merge [PR #30](https://github.com/leonardoborges/bouncer/pull/30): "Prepare for 0.3.2 final release"

- Merge [PR #29](https://github.com/leonardoborges/bouncer/pull/29): "Add min-count/max-count"

- Merge [PR #23](https://github.com/leonardoborges/bouncer/pull/23): "Added date validator"

    ```clojure
    (core/valid? {:dt "2014-04-02"} :dt [[v/datetime]])
    ```
- Merge [PR #27](https://github.com/leonardoborges/bouncer/pull/27): "Cljs support"    

## 0.3.1 (21/10/2014)

- Merge [PR #14](https://github.com/leonardoborges/bouncer/pull/14): "Validation results as error metadata"

This pull request allows users of the library to customise errors messages in a number of different ways. Please see section *"Internationalization and advanced error messages"* in the README.

## 0.3.0 (03/01/2014)

> This release contains breaking changes: most macros have been removed in favour of pure functions and data structure literals. This means a lot less magic and better composability


- `core/valid?` and `core/validate` rewritten as functions
- remove `validators/defvalidatorset` macro in favor of standard maps
- Update README
- Update docstrings
- Add email validation

- Merge [PR #18](https://github.com/leonardoborges/bouncer/pull/18): "Minor readme change to not make it seem a strange design decision was taken, and very minor (somewhat random) syntax change"

## 0.2.3 (12/08/2013)

- Validator sets can now be used at the top level call to `validate` and `valid?`.

    ```clojure
    (defvalidatorset address-validator
    :postcode v/required)

    (defvalidatorset person-validator
    :name v/required
    :age [v/required v/number]
    :address address-validator)

    (core/validate {} person-validator)
    ```

- Added tests and a doc section around validation pipelining. It was an undocumented invariant. See discussion [here](https://github.com/leonardoborges/bouncer/pull/4).
- (alpha) Validators now support a pre-condition option. If it is met, the validator will run, otherwise it's just skipped:

    ```clojure
    (core/valid? {:a 1 :b "X"}
                 :b (v/member #{"Y" "Z"} :pre (comp pos? :a)))

    ;; false
    ```

- Fixed [Issue #5](https://github.com/leonardoborges/bouncer/issues/5): "bouncer.validators/member does not allow me to pass it a symbol referring to the collection"

- Fixed [Issue #7](https://github.com/leonardoborges/bouncer/issues/7): "3 level composition broken"

## 0.2.2 (16/01/2013)

- `defvalidator` now lets you define validators with arbitrary number of arguments.
- All validators are now implemented using `defvalidator`
- New validators:
	- `member` - validates the value is a member of the provided collection
	- `matches`- regex validation (thanks to [ghoseb](https://github.com/ghoseb))
- Updated most validators' docstrings to something less confusing
- Added API documentation using marginalia. See the `docs` folder or [this link](http://leonardoborges.github.com/bouncer/).
- bouncer now stores the error messages in the qualified keyword `:bouncer.core/errors` (thanks to [ghoseb](https://github.com/ghoseb))

  For short, just use an alias:


   ```clojure
  (require '[bouncer.core :as c])
  ;; then somewhere you want to inspect the errors map

  (::c/errors a-map)

  ```
- short-circuit for validators: if a map entry has multiple validators, it stops at the first failure, moving on to the next map entry.
- fixed destructuring bug when using Clojure 1.5 (thanks to [Gary Johnson](gwjohnso@uvm.edu) for reporting it)
- using leiningen profiles to build against Clojure 1.3, 1.4 and 1.5

## 0.2.1 (07/01/2013)

- One step towards composability: new 'defvalidatorset' macro that lets the user encapsulate common validation patterns into reusable units.

## 0.2.0 (04/01/2013)

- Major overhaul of the validation DSL.
- Added the `defvalidator` macro to make defining new validation functions a whole lot simpler.
- Moved built-in validators into the `bouncer.validators` namespace
- New validator:
  - `custom` - for ad-hoc validations using arbitrary functions

## 0.1.0-SNAPSHOT (23/12/2012)

- Initial release.
- Public Macros
    - `validate`
    - `valid`
- Basic built-in validators
    - `required`
    - `number`
    - `positive`
    - `every`
