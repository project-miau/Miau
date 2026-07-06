package miau.ui.clickgui.opal.properties;

import miau.property.Property;
import miau.property.properties.*;
import miau.ui.clickgui.opal.OpalColorUtil;
import miau.ui.clickgui.opal.OpalPanelComponent;
import miau.ui.clickgui.opal.OpalRenderUtil;

/** Base class for Opal property panel renderers. */
public abstract class OpalPropertyPanel extends OpalPanelComponent {

  protected static final int DEFAULT_HEIGHT = 17;

  protected boolean lastProperty;
  protected boolean hidden;

  public OpalPropertyPanel() {
    setHeight(DEFAULT_HEIGHT);
  }

  public boolean isHidden() {
    return hidden;
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    if (lastProperty)
      OpalRenderUtil.roundedRectVarying(
          x, y, width, height, 0, 0, 5, 5, OpalColorUtil.applyOpacity(0xff000000, 0.25F));
    else OpalRenderUtil.rect(x, y, width, height, OpalColorUtil.applyOpacity(0xff000000, 0.25F));
  }

  /** Factory: creates the appropriate OpalPropertyPanel for a Miau Property type. */
  public static OpalPropertyPanel createFor(Property<?> property) {
    if (property instanceof BooleanProperty) {
      return new OpalBooleanPropertyComponent((BooleanProperty) property);
    } else if (property instanceof FloatProperty) {
      FloatProperty fp = (FloatProperty) property;
      if (fp.isDoubleSlider()) {
        return new OpalBoundedNumberPropertyComponent(fp);
      } else {
        return new OpalNumberPropertyComponent(fp);
      }
    } else if (property instanceof IntProperty) {
      IntProperty ip = (IntProperty) property;
      // Wrap as FloatProperty internally for slider rendering
      IntSliderWrapper wrapper = new IntSliderWrapper(ip);
      return new OpalNumberPropertyComponent(wrapper, true);
    } else if (property instanceof PercentProperty) {
      PercentProperty pp = (PercentProperty) property;
      PercentSliderWrapper wrapper = new PercentSliderWrapper(pp);
      return new OpalNumberPropertyComponent(wrapper, true);
    } else if (property instanceof ModeProperty) {
      return new OpalModePropertyComponent((ModeProperty) property);
    } else if (property instanceof ColorProperty) {
      return new OpalColorPropertyComponent((ColorProperty) property);
    } else if (property instanceof TextProperty) {
      return new OpalStringPropertyComponent(property);
    }
    return null;
  }

  /** Adapter: wraps Miau's IntProperty as a FloatProperty for slider use. */
  static class IntSliderWrapper extends FloatProperty {
    private final IntProperty target;

    IntSliderWrapper(IntProperty target) {
      super(
          target.getName(),
          (float) (int) target.getValue(),
          (float) (int) target.getMinimum(),
          (float) (int) target.getMaximum());
      this.target = target;
    }

    @Override
    public boolean setValue(Object object) {
      float val = (Float) object;
      return target.setValue(Math.round(val));
    }

    @Override
    public Float getValue() {
      return (float) (int) target.getValue();
    }

    @Override
    public Float getMin() {
      return (float) (int) target.getMinimum();
    }

    @Override
    public Float getMax() {
      return (float) (int) target.getMaximum();
    }
  }

  /** Adapter: wraps Miau's PercentProperty as a FloatProperty for slider use. */
  static class PercentSliderWrapper extends FloatProperty {
    private final PercentProperty target;

    PercentSliderWrapper(PercentProperty target) {
      super(
          target.getName(),
          (float) (int) target.getValue(),
          (float) target.getMinimum(),
          (float) target.getMaximum());
      this.target = target;
    }

    @Override
    public boolean setValue(Object object) {
      float val = (Float) object;
      return target.setValue(Math.round(val));
    }

    @Override
    public Float getValue() {
      return (float) (int) target.getValue();
    }

    @Override
    public Float getMin() {
      return (float) target.getMinimum();
    }

    @Override
    public Float getMax() {
      return (float) target.getMaximum();
    }
  }
}
