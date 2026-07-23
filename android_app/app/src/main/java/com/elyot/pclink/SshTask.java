package com.elyot.pclink;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;

public class SshTask {

    public static void executeSshCommand(String host, int port, String username, String privateKeyContent, String command) throws Exception {
        JSch jsch = new JSch();

        // Add identity from memory
        jsch.addIdentity("widget_key", privateKeyContent.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, null);

        Session session = jsch.getSession(username, host, port);
        // Avoid strict host key checking for simplicity (not perfectly secure, but common for such apps)
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10000); // 10 seconds timeout

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        channel.connect();
        
        // Wait for command to finish
        while (!channel.isClosed()) {
            Thread.sleep(100);
        }
        
        channel.disconnect();
        session.disconnect();
    }
}
