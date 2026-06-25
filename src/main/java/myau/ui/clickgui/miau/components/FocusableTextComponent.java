package myau.ui.clickgui.miau.components;

public interface FocusableTextComponent {
  boolean isTextInputFocused();

  void unfocusTextInput();

  boolean containsClick(int mouseX, int mouseY);
}
