## 0.2.2 (UNRELEASED)

- `defvalidator` now accepts an arbitrary number arguments.
- All validators are now implemented using `defvalidator`
- New validators:
	- `member` - validates the value is a member of the provided collection
- Updated most validators' docstrings to something less confusing
- Added API documentation using marginalia. See the `docs` folder.

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