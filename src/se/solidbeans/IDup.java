package se.solidbeans;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.FileVisitResult.CONTINUE;

public class IDup extends SimpleFileVisitor<Path> {

    private Map<Path, Long> files = new HashMap<>();
    private Map<Path, Set<Path>> duplicates = new HashMap<>();

    public static void main(String[] args) throws IOException {
        IDup iDup = new IDup();
        Path startingDir = Paths.get(args[0]);
        Files.walkFileTree(startingDir, iDup);
        iDup.findDuplicates();
        iDup.print();
    }

    private void findDuplicates() {
        long date = new Date().getTime();

        files.entrySet().parallelStream().forEach(set -> findDuplicates(set.getKey(), getDupCandidates(set)));

        long diff = new Date().getTime() - date;
        System.out.println("tog: "+diff);

    }

    private void findDuplicates(Path currentFileKey, Map<Path, byte[]> dupCandidates) {
        Set<Path> dup = dupCandidates.entrySet().stream()
                .filter(set -> set.equals(calcChecksum(currentFileKey)))
                .map(set -> set.getKey())
                .collect(Collectors.toSet());
        if (!dup.isEmpty()) {
            this.duplicates.put(currentFileKey, dup);
        }
    }

    private void print() {
        duplicates.forEach((path, map) -> System.out.println(path.toString() + map));
    }

    private Map<Path, byte[]> getDupCandidates(Map.Entry<Path, Long> entry) {
        Map<Path, byte[]> candiates = new HashMap<>();
        files.entrySet().parallelStream()
                .filter(candidate -> isNewDuplicate(candidate, entry))
                .forEach(candidate -> candiates.put(entry.getKey(), calcChecksum(candidate.getKey())));
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
        byte[] checksum = new byte[0];
        files.put(file, attrs.size());
        return CONTINUE;
    }

    private byte[] calcChecksum(Path file)  {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
            md.update(Files.readAllBytes(file));
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        return md.digest();
    }
}
