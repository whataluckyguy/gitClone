import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

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
            // Check if branch already exists
            Path branchPath = Paths.get(".git", "refs", "heads", branchName);
            if(Files.exists(branchPath)){
                System.out.println("Branch '" + branchName + "' already exists.");
                return;
            }

            // Create the new branch reference file
            Files.createDirectories(branchPath.getParent());

            // Create branch at the current HEAD commit, preserving existing history
            Path headPath = Paths.get(".git/HEAD");
            String currentBranchCommitHash = getCurrentCommitHash();

            Files.writeString(branchPath, currentBranchCommitHash);
            System.out.println("Branch '" + branchName + "' created at commit: " + currentBranchCommitHash);

        } catch (IOException e){
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
        byte[] content = Files.readAllBytes(Paths.get(filePath));
//        byte[] content = Files.readAllBytes(file.toPath());

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
//        try(FileWriter writer = new FileWriter(".git/index", true)){
//            writer.write(hash + " " + filePath + "\n");
//        }
        updateIndex(hash,filePath);
    } catch (IOException | NoSuchAlgorithmException e){
        System.out.println("Error adding files: " + e.getMessage());
    }
}

private static void updateIndex(String hash, String filePath) throws IOException {
    File indexFile = new File(".git/index");
    List<String> lines = new ArrayList<>();

    if(indexFile.exists()){
        lines = Files.readAllLines(indexFile.toPath());
//        Remove existing entry for this file if exists
        lines.removeIf(line -> line.endsWith(" " + filePath));
    }

//    Add new entry
    lines.add(hash + " " + filePath);
    Files.write(indexFile.toPath(), lines);
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

public static void merge(String sourceBranchName){
    // Validation
    if(!isGitInitialized()){
        System.out.println("Error: Not a Git repository. Run 'init' first.");
        return;
    }

    try {
        // Get current branch name
        String currentBranch = getCurrentBranch();
        if(currentBranch.equals(sourceBranchName)){
            System.out.println("Cannot merge branch into itself.");
            return;
        }
        // Verify source branch exists
        Path sourceBranchPath = Paths.get(".git", "refs", "heads", sourceBranchName);
        if(!Files.exists(sourceBranchPath)){
            System.out.println("Error: Branch '" + sourceBranchName + "' does not exist.");
            return;
        }

        // Get commit hashes
        String sourceCommitHash = Files.readString(sourceBranchPath).trim();
        String currentCommitHash = getCurrentCommitHash();

        // find merge base (common ancestor)
        String mergeBase = findMergeBase(currentCommitHash, sourceCommitHash);

        // Check if fast-forward is possible
        if(mergeBase.equals(currentCommitHash)){
            performFastForwardMerge(sourceBranchName, sourceCommitHash);
        } else {
            performThreeWayMerge(currentBranch, sourceBranchName, currentCommitHash, sourceCommitHash, mergeBase);
        }

    } catch (Exception e) {
        // TODO: handle exception
        System.out.println("Error during merge: " + e.getMessage());
    }
}

public static void log() {
    if(!isGitInitialized()) {
        System.out.println("Error: Not a Git repository. Run 'init' first.");
        return;
    }

    try {
        Map<String, String> commitToBranch = new HashMap<>();
        List<String> branches = Files.list(Paths.get(".git", "refs", "heads"))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toList());

        // First map main branch commits
        String mainBranch = "main";
        if (branches.contains(mainBranch)) {
            Path mainPath = Paths.get(".git", "refs", "heads", mainBranch);
            String currentCommitHash = Files.readString(mainPath).trim();
            traceCommitLineage(currentCommitHash, mainBranch, commitToBranch);
        }

        // Then map other branch commits (will not override main's commits)
        for(String branch : branches) {
            if (!branch.equals("main")) {
                Path branchPath = Paths.get(".git", "refs", "heads", branch);
                String currentCommitHash = Files.readString(branchPath).trim();
                if (!commitToBranch.containsKey(currentCommitHash)) {
                    traceCommitLineage(currentCommitHash, branch, commitToBranch);
                }
            }
        }

        // Current branch's commit hash
        String currentCommitHash = getCurrentCommitHash();
        
        List<String> commitHistory = new ArrayList<>();
        Set<String> processedCommits = new HashSet<>();

        while (!currentCommitHash.isEmpty()) {
            Path commitPath = Paths.get(".git", "objects",
                    currentCommitHash.substring(0,2),
                    currentCommitHash.substring(2));

            if(!Files.exists(commitPath) || processedCommits.contains(currentCommitHash)){
                break;
            }

            processedCommits.add(currentCommitHash);

            List<String> commitLines = Files.readAllLines(commitPath);
            String parentHash = "";
            String author = "Unknown";
            String message = "";

            for(String line : commitLines){
                if (line.startsWith("parent ")) {
                    parentHash = line.split(" ")[1];
                } else if (line.startsWith("author ")) {
                    author = line.split(" ")[1];
                } else if (line.startsWith("message ")) {
                    message = line.substring(8).trim();
                }
            }

            // Use the original branch for this commit
            String commitBranch = commitToBranch.getOrDefault(currentCommitHash, "main");

            commitHistory.add("Commit: " + currentCommitHash);
            commitHistory.add("Branch: " + commitBranch);
            commitHistory.add("Author: " + author);
            commitHistory.add("Message: " + message);
            commitHistory.add("");

            currentCommitHash = parentHash.isEmpty() ? "" : parentHash;
        }

        // Display commit history
        if(commitHistory.isEmpty()){
            System.out.println("No commit history found.");
        } else {
            for(String entry : commitHistory){
                System.out.println(entry);
            }
        }

    } catch (IOException e) {
        System.out.println("Error reading commit history: " + e.getMessage());
    }
}

//private static List<String> getCommitDetailsForBranch(String currentCommitHash, String branchName) {
//        List<String> commitDetails = new ArrayList<>();
//
//        try {
//            while (!currentCommitHash.isEmpty()) {
//                Path commitPath = Paths.get(".git", "objects", currentCommitHash.substring(0, 2), currentCommitHash.substring(2));
//
//                if(!Files.exists(commitPath)) {
//                    break;
//                }
//
//                List<String> commitLines = Files.readAllLines(commitPath);
//                String parentHash = "";
//                String author = "Unknown";
//                String message = "";
//
//                for (String line : commitLines) {
//                    if (line.startsWith("parent ")) {
//                        parentHash = line.split(" ")[1];
//                    } else if (line.startsWith("author ")) {
//                        author = line.split(" ")[1];
//                    } else if (line.startsWith("message ")) {
//                        message = line.substring(8).trim();
//                    }
//                }
//
//                // If message is empty, try to find the last line
//                if (message.isEmpty() && commitLines.size() > 3) {
//                    message = commitLines.get(commitLines.size() - 1);
//                }
//
//                // Add commit details to history
//                commitDetails.add("Commit: " + currentCommitHash);
//                commitDetails.add("Branch: " + branchName);
//                commitDetails.add("Author: " + author);
//                commitDetails.add("Message: " + message);
//                commitDetails.add("");
//
//                // Move to parent commit
//                currentCommitHash = parentHash.isEmpty() ? "" : parentHash;
//            }
//        } catch (IOException e) {
//            System.out.println("Error reading commit details: " + e.getMessage());
//        }
//
//        return commitDetails;
//    }

private static void traceCommitLineage(String commitHash, String branch, Map<String, String> commitToBranch) {
    try {
        // Only map if not already mapped (preserves original branch association)
        if (!commitToBranch.containsKey(commitHash)) {
            commitToBranch.put(commitHash, branch);

            Path commitPath = Paths.get(".git", "objects",
                    commitHash.substring(0, 2),
                    commitHash.substring(2));

            if (!Files.exists(commitPath)) {
                return;
            }

            List<String> commitLines = Files.readAllLines(commitPath);
            String parentHash = "";

            for (String line : commitLines) {
                if (line.startsWith("parent ")) {
                    parentHash = line.split(" ")[1];
                    break;
                }
            }

            // Recursively trace parent commits
            if (!parentHash.isEmpty()) {
                traceCommitLineage(parentHash, branch, commitToBranch);
            }
        }
    } catch (IOException e) {
        System.out.println("Error tracing commit lineage: " + e.getMessage());
    }
}

private static String getCurrentBranch() throws IOException {
    String headContent = Files.readString(Paths.get(".git", "HEAD")).trim();
    if(headContent.startsWith("ref: refs/heads/")){
        return headContent.substring("ref: refs/heads/".length());
    }
    return "HEAD detached";
}

private static String findMergeBase(String commit1, String commit2) throws IOException {
    Set<String> commit1Ancestors = new HashSet<>();
    collectAncestors(commit1, commit1Ancestors);

    // Walk through commit2's history until we find first common ancestor
    Queue<String> queue = new LinkedList<>();
    queue.offer(commit2);

    while(!queue.isEmpty()){
        String current = queue.poll();
        if(commit1Ancestors.contains(current)){
            return current;
        }

        String parent = getParentCommit(current);
        if(parent != null && !parent.isEmpty()){
            queue.offer(parent);
        }
    }
    return "";
}

private static void collectAncestors(String commitHash, Set<String> ancestors) throws IOException {
    while (commitHash != null && !commitHash.isEmpty()) {
        ancestors.add(commitHash);
        commitHash = getParentCommit(commitHash);
    }
}

private static String getParentCommit(String commitHash) throws IOException {
    Path commitPath = Paths.get(".git", "objects", 
    commitHash.substring(0, 2),
    commitHash.substring(2));

    if(!Files.exists(commitPath)){
        return null;
    }

    List<String> lines = Files.readAllLines(commitPath);
    for(String line : lines){
        if(line.startsWith("parent ")){
            return line.substring(7).trim();
        }
    }
    return null;
}

private static void performFastForwardMerge(String sourceBranchName, String sourceCommitHash) throws IOException{
    // Update current branch reference to point to source branch commit
    String currentBranch = getCurrentBranch();
    Path currentBranchPath = Paths.get(".git", "refs", "heads", currentBranch);
    Files.writeString(currentBranchPath, sourceCommitHash);

    System.out.println("Fast-forward merge complete.");
    System.out.println("Current branch '" + currentBranch + "' is now at " + sourceCommitHash);
}

private static void performThreeWayMerge(String currentBranch, String sourceBranchName,
String currentCommitHash, String sourceCommitHash, String mergeBase) throws IOException, NoSuchAlgorithmException {
    // Get the tree states
    Map<String, String> baseState = getCommitState(mergeBase);
    Map<String, String> currentState = getCommitState(currentCommitHash);
    Map<String, String> sourceState = getCommitState(sourceCommitHash);

    // Merge trees
    Map<String, String> mergedState = mergeTrees(baseState, currentState, sourceState);

    // Create new tree object
    String treeHash = createTreeObject(mergedState);

    // Create merge commit
    StringBuilder commitContent = new StringBuilder();
    commitContent.append("tree ").append(treeHash).append("\n");
    commitContent.append("parent ").append(currentCommitHash).append("\n");
    commitContent.append("parent ").append(sourceCommitHash).append("\n");
    commitContent.append("author ").append(System.getProperty("user.name")).append("\n");
    commitContent.append("message Merge branch '").append(sourceBranchName).append("' into ").append(currentBranch).append("\n");

    String commitHash = computeSHA1(commitContent.toString().getBytes());

    // Save the commit object
    Path commitPath = Paths.get(".git", "objects", commitHash.substring(0,2), sourceCommitHash.substring(2));
    Files.createDirectories(commitPath.getParent());
    Files.write(commitPath, commitContent.toString().getBytes());

    // Update branch reference
    Path branchPath = Paths.get(".git", "refs", "heads", currentBranch);
    Files.writeString(branchPath, commitHash);

    System.out.println("Merge succesful.");
    System.out.println("Created merge commit: " + commitHash);
}

private static Map<String, String> getCommitState(String commitHash) throws IOException {
    Map<String, String> state = new HashMap<>();
    Path commiPath = Paths.get(".git", "objects",
    commitHash.substring(0, 2),
    commitHash.substring(2));

    List<String> lines = Files.readAllLines(commiPath);
    for (String line : lines) {
        if(line.startsWith("tree ")){
            String treeHash = line.substring(5).trim();
            // Read tree object and populate state
            readTreeObject(treeHash, state);
            break;
        }
    }
    return state;
}

private static void readTreeObject(String treeHash, Map<String, String> state) throws IOException {
    Path treePath = Paths.get(".git", "objects", 
    treeHash.substring(0, 2),
    treeHash.substring(2));

    List<String> lines = Files.readAllLines(treePath);
    for(String line : lines){
        String[] parts = line.split(" ");
        if(parts.length == 2){
            state.put(parts[1], parts[0]);
        }
    }
}

private static Map<String, String> mergeTrees(
        Map<String, String> baseState, 
        Map<String, String> currentState, 
        Map<String, String> sourceState) {
    Map<String, String> mergedState = new HashMap<>(currentState);
    
    for (Map.Entry<String, String> entry : sourceState.entrySet()) {
        String file = entry.getKey();
        String sourceHash = entry.getValue();
        String baseHash = baseState.get(file);
        String currentHash = currentState.get(file);
        
        if (baseHash == null) {
            // File added in source
            mergedState.put(file, sourceHash);
        } else if (baseHash.equals(currentHash)) {
            // File unchanged in current but changed in source
            mergedState.put(file, sourceHash);
        } else if (!baseHash.equals(sourceHash) && !baseHash.equals(currentHash)) {
            // Conflict - both branches modified the file
            System.out.println("CONFLICT: " + file + " has conflicts.");
            // For now, we'll take the current version
            // In a real implementation, you'd want to create a proper merge conflict marker
        }
    }
    
    return mergedState;
}

private static String createTreeObject(Map<String, String> state) throws IOException, NoSuchAlgorithmException {
    StringBuilder treeContent = new StringBuilder();
    for (Map.Entry<String, String> entry : state.entrySet()) {
        treeContent.append(entry.getValue()).append(" ").append(entry.getKey()).append("\n");
    }
    
    String treeHash = computeSHA1(treeContent.toString().getBytes());
    Path treePath = Paths.get(".git", "objects", 
            treeHash.substring(0, 2), 
            treeHash.substring(2));
    
    Files.createDirectories(treePath.getParent());
    Files.write(treePath, treeContent.toString().getBytes());
    
    return treeHash;
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
