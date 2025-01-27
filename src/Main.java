import java.util.Arrays;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Please specify a command (e.g., init)");
            return;
        }

        String command = args[0];

        switch(command){
            case "init":
                GitCommands.init();
                break;
            case "add":
                if(args.length < 2){
                    System.out.println("Usage: add <file> or add -all");
                }else if(args[1].equals("--all")) {
                    GitCommands.addAll();
                } else {
//                    GitCommands.add(args[1]);
                    // Reconstruct the full file path by joining all arguments after the "add" command
                    String filePath = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    GitCommands.add(filePath);
                }
                break;
            case "status":
                GitCommands.status();
                break;
            case "commit":
                if(args.length < 2) {
                    System.out.println("Usage: commit <message>");
                } else {
//                    Join all the remaining arguments to form the commit message
                    StringBuilder commitMessage = new StringBuilder();
                    for(int i = 1; i < args.length; i++){
                        commitMessage.append(args[i]).append(" ");
                    }
                    GitCommands.commit(commitMessage.toString().trim());
                }
                break;
            case "log":
                GitCommands.log();
                break;
            case "branch":
                if(args.length < 2){
                    System.out.println("Usage: branch <branch-name>");
                } else {
                    GitCommands.createBranch(args[1]);
                }
                break;
            case "checkout":
                if(args.length < 2){
                    System.out.println("Usage: checkout <branch-name>");
                } else {
                    GitCommands.switchBranch(args[1]);
                }
                break;
            case "merge":
                if(args.length < 2){
                    System.out.println("Usage: merge <branch-name>");
                    System.out.println("Merges the specified branch into the current branch.");
                } else {
                    GitCommands.merge(args[1]);
                }
                break;
            default:
                System.out.println("Unknown command: " + command);
        }
    }
}