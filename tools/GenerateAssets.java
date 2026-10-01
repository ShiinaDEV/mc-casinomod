import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateAssets {
    private static final Path ROOT = Path.of("src/main/resources/assets/shiinacasino/textures");
    private static final int GOLD = 0xffd9b45b, FELT = 0xff073c28, WOOD = 0xff38271d;

    public static void main(String[] arguments) throws Exception {
        Files.createDirectories(ROOT.resolve("block")); Files.createDirectories(ROOT.resolve("item"));
        Files.createDirectories(ROOT.resolve("gui"));
        save("block/wood", texture("wood")); save("block/trim", texture("trim"));
        for (String game : new String[]{"blackjack", "poker", "roulette", "wheel", "exchange"}) save("block/" + game, texture(game));
        BufferedImage chip = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        disk(chip, 8, 10, 6, 0xff24528c); disk(chip, 7, 7, 6, 0xffb62e47);
        disk(chip, 7, 7, 4, GOLD); disk(chip, 7, 7, 3, 0xffca3d56);
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4;
            int horizontal = 7 + (int) Math.round(Math.cos(angle) * 5), vertical = 7 + (int) Math.round(Math.sin(angle) * 5);
            rect(chip, horizontal, vertical, horizontal + 1, vertical + 1, 0xfff7eed8);
        }
        save("item/casino_chip", chip);
        save("gui/felt", texture("felt"));
        save("block/felt", texture("felt"));
        for (String state : new String[]{"button", "button_hover", "button_disabled"}) {
            BufferedImage button = new BufferedImage(128, 20, BufferedImage.TYPE_INT_ARGB);
            int base = state.equals("button_hover") ? 0xff92958c : state.equals("button_disabled") ? 0xff404740 : 0xff74786e;
            rect(button, 0, 0, 127, 19, 0xff151a15); rect(button, 1, 1, 126, 18, base);
            rect(button, 1, 1, 126, 1, 0xffc2c7bc); rect(button, 1, 1, 1, 18, 0xffc2c7bc);
            rect(button, 1, 18, 126, 18, 0xff343b33); rect(button, 126, 1, 126, 18, 0xff343b33);
            for (int vertical = 3; vertical < 17; vertical += 3) for (int horizontal = 3; horizontal < 125; horizontal += 3)
                rect(button, horizontal, vertical, horizontal, vertical, state.equals("button_disabled") ? 0xff454c45 : 0xff838779);
            save("gui/" + state, button);
        }
        BufferedImage back = new BufferedImage(32, 48, BufferedImage.TYPE_INT_ARGB);
        rect(back,0,0,31,47,0xffe7d7bc); rect(back,1,1,30,46,0xfffaf1df);
        rect(back,3,3,28,44,0xff942b36);
        for (int vertical = 5; vertical < 44; vertical += 6) for (int horizontal = 5; horizontal < 29; horizontal += 6) {
            rect(back,horizontal,vertical+1,horizontal+2,vertical+3,0xffcf6f6c);
            rect(back,horizontal+1,vertical,horizontal+1,vertical+4,0xffedb5a5);
        }
        save("gui/card_back",back);
        System.out.println("Generated 14 original pixel texture files.");
    }

    private static BufferedImage texture(String kind) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int vertical = 0; vertical < 32; vertical++) for (int horizontal = 0; horizontal < 32; horizontal++) {
            boolean wood = kind.equals("wood") || kind.equals("exchange");
            int noise = Math.floorMod(horizontal * 73 + vertical * 17 + horizontal * vertical * 3, 13) - 6;
            int base = wood ? WOOD : kind.equals("trim") ? GOLD : FELT;
            int red = Math.max(0, Math.min(255, ((base >>> 16) & 255) + noise));
            int green = Math.max(0, Math.min(255, ((base >>> 8) & 255) + noise));
            int blue = Math.max(0, Math.min(255, (base & 255) + noise));
            image.setRGB(horizontal, vertical, 0xff000000 | red << 16 | green << 8 | blue);
        }
        if (kind.equals("wood")) {
            for (int line = 0; line < 32; line += 8) rect(image, 0, line, 31, line, 0xff201710);
            for (int line = 3; line < 32; line += 8) rect(image, line, 1, line, 6, 0xff463022);
        } else if (!kind.equals("trim") && !kind.equals("felt")) {
            rect(image, 0, 0, 31, 1, GOLD); rect(image, 0, 30, 31, 31, GOLD);
            rect(image, 0, 0, 1, 31, GOLD); rect(image, 30, 0, 31, 31, GOLD);
            switch (kind) {
                case "blackjack" -> {
                    card(image, 7, 10, false); card(image, 16, 10, true);
                    rect(image, 7, 25, 24, 25, GOLD);
                }
                case "poker" -> {
                    for (int index = 0; index < 3; index++) card(image, 4 + index * 8, 8, index == 1);
                    disk(image, 11, 24, 4, 0xffad354b); disk(image, 20, 24, 4, 0xff315aab);
                    disk(image, 11, 24, 2, GOLD); disk(image, 20, 24, 2, GOLD);
                }
                case "roulette", "wheel" -> {
                    disk(image, 16, 16, 13, GOLD);
                    for (int vertical = 4; vertical <= 28; vertical++) for (int horizontal = 4; horizontal <= 28; horizontal++) {
                        int offsetX = horizontal - 16, offsetY = vertical - 16;
                        if (offsetX * offsetX + offsetY * offsetY > 121) continue;
                        int segment = Math.floorMod((int) Math.floor(Math.atan2(offsetY, offsetX) * 12 / (2 * Math.PI)), 12);
                        int color = kind.equals("roulette") ? segment == 0 ? FELT : segment % 2 == 0 ? 0xff252635 : 0xffad354b
                                : new int[]{0xffb93236,0xffe39921,0xff3ba046,0xff239a9f,0xff3266b1,0xff823da5}[segment % 6];
                        image.setRGB(horizontal, vertical, color);
                    }
                    disk(image, 16, 16, 3, GOLD); rect(image, 15, 2, 17, 6, 0xfff7ecd1);
                }
                case "exchange" -> {
                    rect(image, 5, 5, 26, 13, GOLD); rect(image, 7, 7, 24, 11, 0xff152b29);
                    disk(image, 10, 23, 5, 0xffb42e4c); disk(image, 10, 23, 3, GOLD);
                    for (int row = 0; row < 9; row++) {
                        int reach = Math.min(row, 8 - row);
                        rect(image, 23 - reach, 18 + row, 23 + reach, 18 + row, row < 4 ? 0xff9af3ee : 0xff30c2c2);
                    }
                }
                default -> {}
            }
        }
        return image;
    }

    private static void card(BufferedImage image, int horizontal, int vertical, boolean red) {
        rect(image, horizontal, vertical, horizontal + 6, vertical + 10, GOLD);
        rect(image, horizontal + 1, vertical + 1, horizontal + 5, vertical + 9, 0xfff9eddb);
        rect(image, horizontal + 2, vertical + 3, horizontal + 4, vertical + 6, red ? 0xffbc3146 : 0xff222c36);
    }
    private static void disk(BufferedImage image, int centerX, int centerY, int radius, int color) {
        for (int vertical = -radius; vertical <= radius; vertical++) for (int horizontal = -radius; horizontal <= radius; horizontal++)
            if (horizontal * horizontal + vertical * vertical <= radius * radius) rect(image, centerX + horizontal, centerY + vertical, centerX + horizontal, centerY + vertical, color);
    }
    private static void rect(BufferedImage image, int fromX, int fromY, int toX, int toY, int color) {
        for (int vertical = Math.max(0, fromY); vertical <= Math.min(image.getHeight() - 1, toY); vertical++)
            for (int horizontal = Math.max(0, fromX); horizontal <= Math.min(image.getWidth() - 1, toX); horizontal++) image.setRGB(horizontal, vertical, color);
    }
    private static void save(String name, BufferedImage image) throws Exception { ImageIO.write(image, "png", ROOT.resolve(name + ".png").toFile()); }
}
