package miau.ui.clickgui.demise.component.impl;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import miau.property.properties.FloatProperty;
import miau.ui.clickgui.demise.Component;
import miau.util.demise.MouseUtils;
import miau.util.demise.RoundedUtils;
import miau.util.font.FontRepository;
import miau.util.render.ShapeUtil;

public class SliderComponent extends Component {
  private final FloatProperty setting;
  private float anim;
  private float anim2;
  private boolean dragging;
  private boolean dragging2;
  private float previousSetting;

  public SliderComponent(FloatProperty setting) {
    this.setting = setting;
    previousSetting = setting.getValue();
    setHeight(
        FontRepository.getFont("Inter Regular", 15f).height() * 2
            + FontRepository.getFont("Inter Regular", 15f).height()
            + 2);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    FontRepository.getFont("Inter Regular", 15f)
        .draw(setting.getName(), (double) (getX() + 4), (double) (getY()), -1);

    anim =
        animate(
            anim,
            (getWidth() - 8)
                * (setting.getValue() - setting.getMin())
                / (setting.getMax() - setting.getMin()),
            15);
    if (setting.isDoubleSlider() && setting.getSecondValue() != null) {
      anim2 =
          animate(
              anim2,
              (getWidth() - 8)
                  * (setting.getSecondValue() - setting.getMin())
                  / (setting.getMax() - setting.getMin()),
              15);
    }
    float sliderWidth = anim;

    RoundedUtils.drawRound(
        getX() + 4,
        getY() + FontRepository.getFont("Inter Regular", 15f).height() + 2,
        getWidth() - 8,
        2,
        1,
        Color.white.darker().darker().darker().darker());

    if (setting.isDoubleSlider()) {
      float minX = Math.min(anim, anim2);
      float maxX = Math.max(anim, anim2);
      RoundedUtils.drawRound(
          getX() + 4 + minX,
          getY() + FontRepository.getFont("Inter Regular", 15f).height() + 2,
          maxX - minX,
          2,
          1,
          Color.white.darker().darker());
      ShapeUtil.drawFilledCircle(
          getX() + 4 + anim,
          getY() + FontRepository.getFont("Inter Regular", 15f).height() + 3,
          3,
          Color.white.brighter().brighter().getRGB());
      ShapeUtil.drawFilledCircle(
          getX() + 4 + anim2,
          getY() + FontRepository.getFont("Inter Regular", 15f).height() + 3,
          3,
          Color.white.brighter().brighter().getRGB());
    } else {
      RoundedUtils.drawRound(
          getX() + 4,
          getY() + FontRepository.getFont("Inter Regular", 15f).height() + 2,
          sliderWidth,
          2,
          1,
          Color.white.darker().darker());
      ShapeUtil.drawFilledCircle(
          getX() + 4 + sliderWidth,
          getY() + FontRepository.getFont("Inter Regular", 15f).height() + 3,
          3,
          Color.white.brighter().brighter().getRGB());
    }

    FontRepository.getFont("Inter Regular", 15f)
        .draw(
            String.valueOf(setting.getMin()),
            (double) (getX() + 2),
            (double) (getY() + FontRepository.getFont("Inter Regular", 15f).height() * 2 + 2),
            new Color(160, 160, 160).getRGB());
    FontRepository.getFont("Inter Regular", 15f)
        .drawCentered(
            setting.isDoubleSlider()
                ? setting.getValue() + " - " + setting.getSecondValue()
                : String.valueOf(setting.getValue()),
            (double) (getX() + getWidth() / 2),
            (double) (getY() + FontRepository.getFont("Inter Regular", 15f).height() * 2 + 2),
            -1);
    FontRepository.getFont("Inter Regular", 15f)
        .draw(
            String.valueOf(setting.getMax()),
            (double)
                (getX()
                    - 2
                    + getWidth()
                    - FontRepository.getFont("Inter Regular", 15f)
                        .getStringWidth(String.valueOf(setting.getMax()))),
            (double) (getY() + FontRepository.getFont("Inter Regular", 15f).height() * 2 + 2),
            new Color(160, 160, 160).getRGB());

    if (dragging || dragging2) {
      double clampedRatio =
          Math.max(0, Math.min(1, (mouseX - getX() - 4) / (double) (getWidth() - 8)));
      double difference = setting.getMax() - setting.getMin();
      double value = setting.getMin() + clampedRatio * difference;

      float newVal =
          BigDecimal.valueOf(incValue(value, 1.0)).setScale(1, RoundingMode.CEILING).floatValue();
      if (dragging) {
        setting.setValue(newVal);
      } else {
        setting.setSecondValue(newVal);
      }
    }
  }

  public static double incValue(double value, double increment) {
    return Math.round(value / increment) * increment;
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    if (mouseButton == 0
        && MouseUtils.isHovered(
            getX() + 2,
            getY() + FontRepository.getFont("Inter Regular", 15f).height() + 2,
            getWidth(),
            4f,
            mouseX,
            mouseY)) {
      if (setting.isDoubleSlider()) {
        float mouseRelX = mouseX - (getX() + 4);
        if (Math.abs(mouseRelX - anim) <= Math.abs(mouseRelX - anim2)) {
          dragging = true;
        } else {
          dragging2 = true;
        }
      } else {
        dragging = true;
      }
    }
    super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int state) {
    if (state == 0) {
      dragging = false;
      dragging2 = false;
    }
    super.mouseReleased(mouseX, mouseY, state);
  }

  @Override
  public boolean isVisible() {
    return setting.isVisible();
  }

  @Override
  public boolean isChild() {
    return false;
  }

  private float animate(float current, float target, float speed) {
    return current + (target - current) / Math.max(1, speed);
  }
}
