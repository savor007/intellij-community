import java.util.*;

class Foo {
  void m() {
    Arrays.asList("a", "b").stream().toUnmod<caret>
  }
}