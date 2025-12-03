/**
 * @author Marc Ishi et Arnaut Leyre
**/
package ch.heigvd.dai.jitsus.game;

public class CardSus {
    public enum family {
        Knife,
        Fist,
        Acid,
        Gun
    }

    private family fam;
    private int value;

    CardSus(family fam, int value) {
        if (value < 0 || value > 10) {
            System.out.println("Invalid value, correction in the correct range with the operator modulus");
            value = (value % 9) + 1;
        }
        this.fam = fam;
        this.value = value;
    }

    /**
     * @return a string emojie version of family
     */
    public String familyToString() {
        return switch (fam) {
            case Knife -> "\uD83D\uDD2A";
            case Gun -> "\uD83D\uDD2B";
            case Fist -> "\uD83D\uDC4A";
            case Acid -> "\uD83E\uDDEA";
        };
    }

    @Override
    public String toString() {
        return familyToString() + value;
    }

    public int famToInt(){
        return fam.ordinal();
    }

    public int getVal(){
        return value;
    }
}
