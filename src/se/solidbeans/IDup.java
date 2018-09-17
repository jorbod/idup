package se.solidbeans;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.file.FileVisitResult.CONTINUE;

public class IDup extends SimpleFileVisitor<Path> {

    private Map<Path, byte[]> files = new HashMap<>();

    public static void main(String[] args) throws IOException {
        IDup iDup = new IDup();
        Path startingDir = Paths.get(args[0]);
        Files.walkFileTree(startingDir, iDup);
        iDup.print();
    }

    private void print() {
        long date = new Date().getTime();
        files.forEach(this::checkDup);
        long diff = new Date().getTime() - date;
        System.out.println("tog: "+diff);
    }

    private void checkDup(Path path, byte[] bytes) {
        if (files.values().stream().parallel().filter(entry -> Arrays.equals(entry, bytes)).count() > 1) {
            System.out.println(path);
        }
    }


    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        super.visitFile(file, attrs);
        byte[] checksum = new byte[0];
        try {
            checksum = calcChecksum(file);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        files.put(file, checksum);
        return CONTINUE;
    }

    private byte[] calcChecksum(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(Files.readAllBytes(file));
        return md.digest();
    }
}
