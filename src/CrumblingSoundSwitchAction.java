import com.codepoetics.protonpack.collectors.CollectorUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CrumblingSoundSwitchAction extends AnAction {

    final byte[] BLANK_CHARACTERS = {0x09, 0x20};
    MessageBusConnection connection;

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        assert project != null;

        if (this.connection == null) {
            this.enable(project);
        } else {
            disable();
        }
    }

    private void enable(Project project) {
        BulkFileListener listener = this.createIndentPatrolListener(project);
        this.connection = project.getMessageBus().connect();
        this.connection.subscribe(VirtualFileManager.VFS_CHANGES, listener);
        Notification notification = new Notification(
                "crumbling-sound-plugin",
                "crumbling-sound-plugin",
                "有効化しましたぜ",
                NotificationType.INFORMATION
        );
        Notifications.Bus.notify(notification);
    }

    private void disable() {
        this.connection.disconnect();
        this.connection = null;
        Notification notification = new Notification(
                "crumbling-sound-plugin",
                "crumbling-sound-plugin",
                "無効化しましたぜ",
                NotificationType.INFORMATION
        );
        Notifications.Bus.notify(notification);
    }

    private BulkFileListener createIndentPatrolListener(Project project) {
        return new BulkFileListener() {

            List<CrumblingChecker> checkers;

            private byte[] getFileContent(VFileEvent event) {
                VirtualFile file = event.getFile();
                assert file != null;
                try {
                    return file.contentsToByteArray();
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public void before(@NotNull List<? extends VFileEvent> events) {
                String ideaPath = project.getBasePath() + "/.idea";
                this.checkers = events.stream()
                        .filter(event -> !event.getPath().startsWith(ideaPath))
                        .map(event -> {
                            CrumblingChecker checker = new CrumblingChecker(event.getPath());
                            checker.setBefore(Objects.requireNonNull(this.getFileContent(event)));
                            return checker;
                        }).collect(Collectors.toList());
            }

            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                events.forEach(event -> {
                    CrumblingChecker checker = this.checkers.stream()
                            .filter(c -> c.getPath().equals(event.getPath()))
                            .findFirst()
                            .orElse(null);
                    assert checker != null;
                    checker.setAfter(Objects.requireNonNull(this.getFileContent(event)));
                });
                int count = checkers.stream().mapToInt(CrumblingChecker::check).sum();
                if (count <= 0) return;

                Notification notification = new Notification(
                        "crumbling-sound-plugin",
                        "crumbling-sound-plugin",
                        "ぶつかった！",
                        NotificationType.INFORMATION
                );
                Notifications.Bus.notify(notification);
            }
        };
    }

    class CrumblingChecker {
        private String path;
        private byte[] before, after;
        diff_match_patch dmp = new diff_match_patch();

        CrumblingChecker(String path) {
            this.path = path;
        }

        int check() {
            LinkedList<diff_match_patch.Diff> diff = dmp.diff_main(new String(this.before), new String(this.after));
            dmp.diff_cleanupSemantic(diff);
            System.out.println(diff);
            return (int)StreamEx.ofSubLists(diff, 2, 1)
                    .filter(this::checkPair).count();
        }

        boolean checkPair(List<diff_match_patch.Diff> pair) {
            return checkPair(pair.get(0), pair.get(1));
        }

        boolean checkPair(diff_match_patch.Diff first, diff_match_patch.Diff second) {
            Pattern spaceStartPattern = Pattern.compile("[\\s]+.+", Pattern.DOTALL);
            Pattern spaceEndPattern = Pattern.compile(".+[\\s]+", Pattern.DOTALL);

            return diff_match_patch.Operation.DELETE.equals(first.operation) &&
                    spaceEndPattern.matcher(first.text).matches() &&
                    !spaceStartPattern.matcher(second.text).matches();
        }

        public String getPath() {
            return this.path;
        }

        public byte[] getBefore() {
            return before.clone();
        }

        public byte[] getAfter() {
            return after.clone();
        }

        public void setBefore(byte[] before) {
            this.before = before.clone();
        }

        public void setAfter(byte[] after) {
            this.after = after.clone();
        }
    }
}
