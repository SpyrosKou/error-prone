When code calls a non-`void` method, it should usually use the value that the
method returns.

Consider the following code, which ignores the return value of `concat`:

```java
string.concat("\n");
```

That code is a no-op because `concat` doesn't modify `string`; it returns a new
string for the caller to use, as in:

```java
string = string.concat("\n");
```

To avoid this bug, Error Prone requires callers to use the return value of
`concat` and some other well-known methods.

Additionally, Error Prone can be configured to require callers to use the return
value of any methods that you choose.

### How to tell Error Prone which methods to check

Most methods are like `concat`: Calls to those methods must use their return
values.

However, there are exceptions. For example, `set.add(element)` returns a
`boolean`: The return value is `false` if `element` was *already* contained in
`set`. Typically, callers don't need to know this, so they don't need to use the
return value.

For Error Prone's `CheckReturnValue` check to be useful, it needs to know which
methods are like `concat` and which are like `add`.

#### `@CheckReturnValue` and `@CanIgnoreReturnValue`

The `@CheckReturnValue` annotation (available in JSR-305[^jsr] or in
[Error Prone][epcrv]) marks methods whose return values must be used. This error
is triggered when one of these methods is called but the result is not used.

[^jsr]: Of note, the JSR-305 project was [never fully approved][jsr305], so the
    JSR-305 version of the annotation is not actually official and causes
    issues with Java 9 and the [Module System][j9jsr305]. Prefer to use the
    Error Prone version.

`@CheckReturnValue` may be applied to a class or package [^package-info] to
indicate that all methods in that class or package must have their return values
used.

For convenience, we provide an annotation, [`@CanIgnoreReturnValue`][epcirv], to
exempt specific methods or classes from this behavior. `@CanIgnoreReturnValue`
is available from the Error Prone annotations package,
`com.google.errorprone.annotations`.

[^package-info]: To annotate a package, create a
    `package-info.java` file in the package directory, add a package statement,
    and annotate the package statement.

If you really want to ignore the return value of a method annotated with
`@CheckReturnValue`, a cleaner alternative to `@SuppressWarnings` is to assign
the result to a variable that starts with `unused`:

```java
public void setNameFormat(String nameFormat) {
  String unused = format(nameFormat, 0); // fail fast if the format is bad or null
  this.nameFormat = nameFormat;
}
```


### Ignored contexts

`@CheckReturnValue` is ignored under the following conditions (which saves users
from having to use either an `unused` variable or `@SuppressWarnings`):

1.  Calls from `Mockito.verify()` or `Stubber.when()`; e.g.,
    `Mockito.verify(t).foo()` or `doReturn(val).when(t).foo()` (where `foo()` is
    annotated with `@CheckReturnValue`). Here, the method calls are just used to
    program the mock object, not to be consumed directly.

2.  Code that does exception testing with JUnit, where the intent is that the
    method call should throw an exception:

    *   Uses of JUnit 4.13 or JUnit5's `assertThrows` methods:

        ```java
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
        ```

    *   The `try/execute/fail/catch` pattern

        ```java
         try {
           list.get(-1);
           fail("Expected a IndexOutOfBoundsException to be thrown on a negative index");
         } catch (IndexOutOfBoundsException expected) {
         }
        ```

    *   JUnit's `ExpectedException`

        ```java
        expectedException.expect(IndexOutOfBoundsException.class);
        list.get(-1); // If this throws IOOBE, the test passes.
        ```

[epcrv]: https://errorprone.info/api/latest/com/google/errorprone/annotations/CheckReturnValue.html
[epcirv]: https://errorprone.info/api/latest/com/google/errorprone/annotations/CanIgnoreReturnValue.html
[j9jsr305]: https://blog.codefx.org/java/jsr-305-java-9/
[jsr305]: https://jcp.org/en/jsr/detail?id=305
