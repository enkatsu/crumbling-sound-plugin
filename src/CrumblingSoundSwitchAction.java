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
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                System.out.println("*** path ***");
                this.checkers.stream().map(CrumblingChecker::getPath).forEach(System.out::println);
                System.out.println("*** before ***");
                this.checkers.stream().map(CrumblingChecker::getBefore).forEach(System.out::println);
                System.out.println("*** byte ***");
                this.checkers.stream().map(checker -> new String(Hex.encodeHex(checker.getBefore()))).forEach(System.out::println);
                System.out.println("*** after ***");
                this.checkers.stream().map(CrumblingChecker::getAfter).forEach(System.out::println);
                System.out.println("*** byte ***");
                this.checkers.stream().map(checker -> new String(Hex.encodeHex(checker.getAfter()))).forEach(System.out::println);
                System.out.println("***");

                Notification notification = new Notification(
                        "crumbling-sound-plugin",
                        "crumbling-sound-plugin",
                        "このインデントは壊れてますね",
                        NotificationType.INFORMATION
                );
                Notifications.Bus.notify(notification);
            }
        };
    }

    class CrumblingChecker {
        private String path;
        private byte[] before, after;

        CrumblingChecker(String path) {
            this.path = path;
        }

        boolean check() {
            return true;
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
