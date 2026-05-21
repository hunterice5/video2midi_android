package com.video2midi.core;

import com.video2midi.model.KeyPosition;
import com.video2midi.model.Preferences;
import com.video2midi.utils.MathUtils;
import java.util.ArrayList;
import java.util.List;

public class KeyPositionCalculator {

    public static void updateKeyPositions(Preferences prefs) {
        List<KeyPosition> positions = new ArrayList<>();

        double xx = 0;
        int keysPosCount = prefs.getKeysPosCount();
        double keyw  = prefs.getWhiteKeyWidth() * 0.5f;

        for (int idx = 0; idx < keysPosCount; idx++) {
            int i = idx / 12;  // октава
            int j = idx % 12;  // нота в октаве

            KeyPosition pos = new KeyPosition((int) Math.round(xx), 0);

            // Проверяем, является ли клавиша черной
            if (isBlackKey(j)) {
                pos.setY(prefs.getYOffsetBlackKeys());
                xx -= keyw;

                // Позиционирование черных клавиш относительно белых
                if (j == 1 || j == 6) {
                    pos.setX((int) Math.round(xx + keyw * prefs.getBlackKeyRelativePosition()));
                } else if (j == 8) {
                    pos.setX((int) Math.round(xx + keyw * 0.5));
                } else if (j == 3 || j == 10) {
                    pos.setX((int) Math.round(xx + keyw * (1.0 - prefs.getBlackKeyRelativePosition())));
                }
            }
            xx += keyw;

            // Применяем поворот
            double[] rotated = MathUtils.rotate( pos.getX(), pos.getY(), prefs.getKeysAngle() );

            pos.setX((int) Math.round(-rotated[0]));
            pos.setY((int) Math.round(rotated[1]));

            positions.add(pos);
        }

        prefs.setKeysPositions(positions);
    }

    public static boolean isBlackKey(int noteInOctave) {
        return noteInOctave == 1 || noteInOctave == 3 || 
               noteInOctave == 6 || noteInOctave == 8 || 
               noteInOctave == 10;
    }
    
    public static boolean isBlackKeyByIndex(int keyIndex) {
        return isBlackKey(keyIndex % 12);
    }
}
