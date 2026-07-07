package miau.util;

import net.minecraft.init.Items;
import java.lang.reflect.Field;

public class FindItems {
    public static void print() {
        try {
            for (Field f : Items.class.getFields()) {
                System.out.println(f.getName() + " " + f.getType().getName());
            }
        } catch (Exception e) {}
    }
}
