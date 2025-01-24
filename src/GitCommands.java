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
//            Create refs/heads/main
            createBranch("main");
//            set HEAD to main branch
            Files.writeString(Paths.get(".git/HEAD"), "ref: refs/heads/main");
        } catch (IOException e){
            System.out.println("Error creating Git files: " + e.getMessage());
        }
    } else {
        System.out.println("Failed to create .git repository.");
    }
}

public static void createBranch(String branchName) {
    if(!isGitInitialized()){
        System.out.println("Error: Not a Git repository. Run 'init' first.");
        return;
    }

    try{
//        create the new branch reference file
        Path branchPath = Paths.get(".git", "refs", "heads", branchName);
        Files.createDirectories(branchPath.getParent());
        Files.writeString(branchPath, getCurrentCommitHash());
        System.out.println("Branch '" + branchName + "' created.");

    }catch (IOException e){
        System.out.println("Error creating branch: " + e.getMessage());
    }
}

public static void switchBranch(String branchName){
    if(!isGitInitialized()){
        System.out.println("Error: Not a Git repository. Run 'init' first.");
        return;
    }
    Path branchPath = Paths.get(".git", "refs", "heads", branchName);
    if(!Files.exists(branchPath)){
        System.out.println("Error: Branch '" + branchName + "' does not exist.");
        return;
    }
    try{
        Files.writeString(Paths.get(".git/HEAD"), "ref: refs/heads/" + branchName);
        System.out.println("Switched to branch '" + branchName + "'.");
    } catch (IOException e){
        System.out.println("Error switching branch: " + e.getMessage());
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

    File indexFile = new File(".git/index");
    if(!indexFile.exists() || indexFile.length() == 0){
        System.out.println("No changes staged for commit.");
        return;
    }

    try {
        List<String> indexLines = Files.readAllLines(indexFile.toPath());

        StringBuilder commitContent = new StringBuilder();
        commitContent.append("tree " + computeTreeHash(indexLines) + "\n");

        // Get current branch from HEAD
        String headContent = Files.readString(Paths.get(".git/HEAD")).trim();
        String currentBranch = headContent.startsWith("ref: ")
                ? headContent.substring(5)
                : "refs/heads/main";  // Default to main if no branch selected

        // Get parent commit for the current branch
        Path branchPath = Paths.get(".git", currentBranch);
        String parentCommitHash = Files.exists(branchPath)
                ? Files.readString(branchPath).trim()
                : "";

        if (!parentCommitHash.isEmpty()) {
            commitContent.append("parent " + parentCommitHash + "\n");
        }

        commitContent.append("author " + System.getProperty("user.name") + "\n");
        commitContent.append("message " + message + "\n");

        String commitHash = computeSHA1(commitContent.toString().getBytes());

        // Store commit object
        Path commitPath = Paths.get(".git", "objects", commitHash.substring(0, 2), commitHash.substring(2));
        Files.createDirectories(commitPath.getParent());
        Files.write(commitPath, commitContent.toString().getBytes());

        // Update current branch with new commit hash
        Files.writeString(branchPath, commitHash);

        System.out.println("Committed to branch '" +
                currentBranch.substring(currentBranch.lastIndexOf('/') + 1) +
                "' with hash: " + commitHash);

        // Save last committed state and clear index
        Path lastCommitStatePath = Paths.get(".git", "last_commit_state");
        Files.write(lastCommitStatePath, Files.readAllBytes(indexFile.toPath()));
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

        try{
            String headContent = Files.readString(Paths.get(".git/HEAD")).trim();
            String currentBranch = headContent.startsWith("ref: ")
                    ? headContent.substring(5)
                    : "refs/heads/main";

//            Get current commit hash for the branch
            Path branchPath = Paths.get(".git", currentBranch);
            if(!Files.exists(branchPath)){
                System.out.println("Error: Current branch not found.");
                return;
            }

            //    Read the current commit hash from HEAD
            String currentCommitHash = Files.readString(branchPath).trim();
            if(currentCommitHash.isEmpty()){
                System.out.println("No commits found on branch: " + currentBranch.substring(currentBranch.lastIndexOf('/') + 1));
                return;
            }
            List<String> commitHistory = new ArrayList<>();
            while (!currentCommitHash.isEmpty()){
                Path commitPath = Paths.get(".git", "objects", currentCommitHash.substring(0, 2), currentCommitHash.substring(2));

                if(!Files.exists(commitPath)){
                    System.out.println("Error: Commit not found: " + currentCommitHash);
                    break;
                }
                List<String> commitLines = Files.readAllLines(commitPath);
                String commitContent = String.join("\n", commitLines);
                String parentHash = "";
                String author = "Unknown";
                String message = "";

                for (String line : commitLines) {
                    if (line.startsWith("parent ")) {
                        parentHash = line.split(" ")[1];
                    } else if (line.startsWith("author ")) {
                        author = line.split(" ")[1];
                    } else if (line.startsWith("message ")) {
                        message = line.substring(8).trim();
                    }
                }

                // If message is empty, try to find the last line
                if (message.isEmpty() && commitLines.size() > 3) {
                    message = commitLines.get(commitLines.size() - 1);
                }

//                Add commit details to history
                commitHistory.add("Commit: " + currentCommitHash);
                commitHistory.add("Branch: " + currentBranch.substring(currentBranch.lastIndexOf('/') + 1));
                commitHistory.add("Author: " + author);
                commitHistory.add("Message: " + message);
                commitHistory.add("");

//                Move to parent commit
                currentCommitHash = parentHash.isEmpty() ? "" : parentHash;
            }
//            Display commit history
            if(commitHistory.isEmpty()){
                System.out.println("No commit history found.");
            }else {
                for(String entry : commitHistory){
                    System.out.println(entry);
                }
            }

        }catch (IOException e){
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

    private static String getCurrentCommitHash(){
    Path headPath = Paths.get(".git/HEAD");
    if(Files.exists(headPath)){
        try{
            String headContent = Files.readString(headPath).trim();
            if(headContent.startsWith("ref: ")){
//                If head points to a branch, read the commit hash from that branch
                Path branchPath = Paths.get(".git", headContent.substring(5));
                if(Files.exists(branchPath)){
                    return Files.readString(branchPath).trim();
                }
            }
//            Fallback to direct hash if not a branch reference
            return headContent;
        } catch (IOException e){
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
