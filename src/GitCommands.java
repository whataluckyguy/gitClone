import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class GitCommands {
public static void init(){
    File gitDir = new File(".git");
    if(gitDir.exists()){
        System.out.println("A Git repository already exists here.");
        return;
    }

    if(gitDir.mkdir()){
        try{
            new File(gitDir, "HEAD").createNewFile();
            new File(gitDir, "config").createNewFile();
            new File(gitDir, "description").createNewFile();
            new File(gitDir, "index").createNewFile();
            System.out.println("Initialized empty Git repository in " + gitDir.getAbsolutePath());

        } catch (IOException e){
            System.out.println("Error creating Git files: " + e.getMessage());
        }
    } else {
        System.out.println("Failed to create .git repository.");
    }
}

public static void add(String filePath) {
    if(!isGitInitialized()){
        System.out.println("Error: Not a Git repository. Run 'init' first.");
        return;
    }

    File file = new File(filePath);
    if(!file.exists()){
        System.out.println("File not found: " + filePath);
        return;
    }

    try{
//        compute SHA-1 hash
        byte[] content = Files.readAllBytes(file.toPath());
        String hash = computeSHA1(content);

//        check if file is already in the index with the same hash
        if(isFileInIndex(hash, filePath)){
            System.out.println("No changes detected for: " + filePath);
            return;
        }

//        store object in .git/objects/<hash-prefix>/<hash-suffix>
        Path objectPath = Paths.get(".git", "objects", hash.substring(0, 2), hash.substring(2));
        Files.createDirectories(objectPath.getParent());
        Files.write(objectPath, content);

//      update index
        try(FileWriter writer = new FileWriter(".git/index", true)){
            writer.write(hash + " " + filePath + "\n");
        }
    } catch (IOException | NoSuchAlgorithmException e){
        System.out.println("Error adding files: " + e.getMessage());
    }
}

private static boolean isFileInIndex(String hash, String filePath) throws IOException {
    // First check the current index
    File indexFile = new File(".git/index");
    if (indexFile.exists()) {
        List<String> lines = Files.readAllLines(indexFile.toPath());
        String entry = hash + " " + filePath;
        if (lines.stream().anyMatch(line -> line.equals(entry))) {
            return true;
        }
    }

    // Then check the last committed state
    Path lastCommitStatePath = Paths.get(".git", "last_commit_state");
    if (Files.exists(lastCommitStatePath)) {
        List<String> lines = Files.readAllLines(lastCommitStatePath);
        String entry = hash + " " + filePath;
        return lines.stream().anyMatch(line -> line.equals(entry));
    }

    return false;
}

public static void addAll(){
    if(!isGitInitialized()){
        System.out.println("Error: Not a Git repository. Run 'init' first.");
        return;
    }

    File currentDir = new File(".");
    File[] files = currentDir.listFiles();

    if(files == null) return;

    for(File file : files){
        if(file.isFile() && !file.getName().startsWith(".git") && !file.getName().equals("Lit.jar")){
            try{
                //            compute SHA-1 hash of the file content
                byte[] content = Files.readAllBytes(file.toPath());
                String hash = computeSHA1(content);

//            Check if the file is already staged with the same hash
                if(!isFileInIndex(hash, file.getPath())){
                    add(file.getPath());
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                System.out.println("Error adding files: " + e.getMessage());
            }
        }
    }
}

public static void commit(String message) {
    if(!isGitInitialized()){
        System.out.println("Error: Not a Git repository. Run 'init' first.");
        return;
    }

//    read index to determine if there are any staged changes.
    File indexFile = new File(".git/index");
    if(!indexFile.exists() || indexFile.length() == 0){
        System.out.println("No changes staged for commit.");
        return;
    }

    try{
//        Read the index file to get the list of staged files
        List<String> indexLines = Files.readAllLines(indexFile.toPath());
//        create commit object content
        StringBuilder commitContent = new StringBuilder();
        commitContent.append("tree " + computeTreeHash(indexLines) + "\n");
        commitContent.append("parent " + getCurrentCommitHash() + "\n");
        commitContent.append("author " + System.getProperty("user.name") + "\n");
        commitContent.append("message " + message + "\n");

//        compute commit hash
        String commitHash = computeSHA1(commitContent.toString().getBytes());
//        Store commit object
        Path commitPath = Paths.get(".git", "objects", commitHash.substring(0, 2), commitHash.substring(2));
        Files.createDirectories(commitPath.getParent());
        Files.write(commitPath, commitContent.toString().getBytes());

//        Update HEAD and branch reference
        Files.writeString(Paths.get(".git/HEAD"), commitHash);

        System.out.println("Committed with hash: " + commitHash);

        // Instead of clearing the index, save it as the last committed state
        Path lastCommitStatePath = Paths.get(".git", "last_commit_state");
        Files.write(lastCommitStatePath, Files.readAllBytes(indexFile.toPath()));
        
        // Now clear the index
        Files.writeString(indexFile.toPath(), "");
    } catch (IOException | NoSuchAlgorithmException e) {
        System.out.println("Error committing changes: " + e.getMessage());
    }
}

    public static void log() {
        if(!isGitInitialized()){
            System.out.println("Error: Not a Git repository. Run 'init' first.");
            return;
        }

//    Read the current commit hash from HEAD
        String currentCommitHash = getCurrentCommitHash();
        if(currentCommitHash.isEmpty()){
            System.out.println("No commits found.");
            return;
        }

        List<String> commitHistory = new ArrayList<>();
        try{
//        Traversing the commit history
            while (!currentCommitHash.isEmpty()){
                Path commitPath = Paths.get(".git", "objects", currentCommitHash.substring(0, 2), currentCommitHash.substring(2));
                if(!Files.exists(commitPath)){
                    System.out.println("Error: Commit object not found.");
                    break;
                }

//            Read the commit object content
                List<String> commitLines = Files.readAllLines(commitPath);
                StringBuilder commitContent = new StringBuilder();
                for(String line : commitLines) {
                    commitContent.append(line).append("\n");
                }

//            Parse the commit content
                String[] parts = commitContent.toString().split("\n");
                if(parts.length < 4) {
                    System.out.println("Error: Malformed commit object.");
                    break;
                }
                String treeHash = parts[0].split(" ")[1];
                String parentHash = parts[1].split(" ").length > 1 ? parts[1].split(" ")[1] : "";
                String author = parts[2].split(" ").length > 1 ? parts[2].split(" ")[1] : "";
//                String message = parts[3].split(" ").length > 1 ? parts[3].split(" ")[1] : "";
                StringBuilder messageBuilder = new StringBuilder();
                for(int i = 3; i < parts.length; i++){
                    if(parts[i].startsWith("message ")){
                        messageBuilder.append(parts[i].substring(8));
                    } else {
                        messageBuilder.append(parts[i]);
                    }
                    if(i != parts.length - 1){
                        messageBuilder.append(" ");
                    }
                }
                String message = messageBuilder.toString().trim();


//            Add the commit details to the history
                commitHistory.add("Commit: " + currentCommitHash);
                commitHistory.add("Author: " + author);
                commitHistory.add("Message: " + message);
                commitHistory.add("");

//            Move to the parent commit
                currentCommitHash = parentHash.equals("null") ? "" : parentHash;
            }
//        Display the commit history
            for(String entry : commitHistory){
                System.out.println(entry);
            }
        } catch (IOException e){
            System.out.println("Error reading commit history: " + e.getMessage());
        }
    }

public static String computeSHA1(byte[] input) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    byte[] hashBytes = digest.digest(input);
    StringBuilder hash = new StringBuilder();
    for(byte b : hashBytes){
        hash.append(String.format("%02x", b));
    }
    return hash.toString();
}

private static String computeTreeHash(List<String> indexLines) throws NoSuchAlgorithmException {
    StringBuilder treeContent = new StringBuilder();
    for(String line : indexLines) {
        treeContent.append(line).append("\n");
    }
    return computeSHA1(treeContent.toString().getBytes());
}

//private static String getCurrentCommitHash() throws IOException {
//    Path headPath = Paths.get(".git/HEAD");
//    if(Files.exists(headPath)){
//        return Files.readString(headPath).trim();
//    }
//    return "";
//}

    private static String getCurrentCommitHash() {
        Path headPath = Paths.get(".git/HEAD");
        if (Files.exists(headPath)) {
            try {
                return Files.readString(headPath).trim();
            } catch (IOException e) {
                System.out.println("Error reading HEAD: " + e.getMessage());
            }
        }
        return "";
    }

public static void status(){
    if(!isGitInitialized()){
        System.out.println("Error: Not a Git repository. Run 'init' first.");
        return;
    }

    File indexFile = new File(".git/index");
    if(!indexFile.exists()){
        System.out.println("No files staged for commit.");
        return;
    }
    try{
        List<String> lines = Files.readAllLines(indexFile.toPath());
        if(lines.isEmpty()){
            System.out.println("No files staged for commit.");
        } else {
            System.out.println("Files staged for commit:");
            for(String line : lines){
                String[] parts = line.split(" ");
                if(parts.length == 2){
                    System.out.println(parts[1]);
                }
            }
        }
    } catch (IOException e){
        System.out.print("Error reading index: " + e.getMessage());
    }
}

public static boolean isGitInitialized(){
    File gitDir = new File(".git");
    return gitDir.exists() && gitDir.isDirectory();
}

}
