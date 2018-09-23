package se.solidbeans;

import javax.xml.bind.DatatypeConverter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.nio.file.FileVisitResult.CONTINUE;

public class IDup extends SimpleFileVisitor<Path> {

    private Map<Path, Long> files = new HashMap<>();
    private Map<Path, Set<Path>> duplicates = new ConcurrentSkipListMap<>();
    private Integer dotcount;

    public static void main(String[] args) throws IOException {
        IDup iDup = new IDup();
        Path startingDir = Paths.get(args[0]);
        System.out.println("Reading files "+args[0]);
        Files.walkFileTree(startingDir, iDup);
        System.out.println("Finding duplicates...");
        iDup.findDuplicates();
        iDup.print();
    }

    private void findDuplicates() {
        long date = new Date().getTime();
        files.entrySet().parallelStream().forEach(entry -> findDuplicates(entry.getKey(), getDupCandidates(entry)));
        long diff = new Date().getTime() - date;
        System.out.println("\n2"+TimeUnit.MILLISECONDS.toSeconds(diff));
    }

    private void findDuplicates(Path currentFileKey, Map<Path, String> dupCandidates) {
        Set<Path> dup = dupCandidates.entrySet().stream()
                .filter(set -> set.getValue().equals(calcChecksum(currentFileKey)))
                .map(set -> set.getKey())
                .collect(Collectors.toSet());
        if (!dup.isEmpty()) {
            this.duplicates.put(currentFileKey, dup);
            if (dotcount == null) {
                dotcount = 0;
            } else if (dotcount == 80) {
                System.out.println();
                dotcount =0;
            }
            dotcount++;
            System.out.print(".");
        }
    }

    private Map<Path, String> getDupCandidates(Map.Entry<Path, Long> entry) {
        Map<Path, String> candiates = new HashMap<>();
        files.entrySet().parallelStream()
                .filter(candidate -> isNewDuplicate(candidate, entry))
                .forEach(candidate -> candiates.put(candidate.getKey(), calcChecksum(candidate.getKey())));
        return candiates;
    }

    private boolean isNewDuplicate(Map.Entry<Path, Long> candidate, Map.Entry<Path, Long> entry) {
        return entry != candidate && entry.getValue().equals(candidate.getValue()) &&
                !(isDuplicate(candidate) || isDuplicate(entry));
    }

    private boolean isDuplicate(Map.Entry<Path, Long> set) {
        return duplicates.containsKey(set.getKey()) ||
                duplicates.values().stream().anyMatch(dupSet -> dupSet.contains(set.getKey()));
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        super.visitFile(file, attrs);
        if (prededate(file)) {
            files.put(file, attrs.size());
        }
        return CONTINUE;
    }

    private boolean prededate(Path file) {
        if (file==null )return false;
        return !file.toString().contains("iPhoto-bibliotek");
    }

    private String calcChecksum(Path file)  {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA1");
            FileInputStream fileInputStream = new FileInputStream(file.toFile());
            FileChannel channel = fileInputStream.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                md.update(buffer);
                buffer.clear();
            }
            fileInputStream.close();
        } catch (NoSuchAlgorithmException | OutOfMemoryError | IOException e) {
            System.out.println("Stor fil: " +file.toString());
            e.printStackTrace();
        }
        return DatatypeConverter
                .printHexBinary(md.digest());
    }

    private void print() {
        duplicates.entrySet().forEach(this::printEntry);
        System.out.println("Antal duplikat: "+duplicates.size()+". Antal filer: "+files.size());
    }

    private void printEntry(Map.Entry<Path, Set<Path>> e) {
        System.out.println();
        System.out.println(e.getKey().toString());
        e.getValue().forEach(v -> System.out.println("            "+v.toString()));
        System.out.println();
    }
}
