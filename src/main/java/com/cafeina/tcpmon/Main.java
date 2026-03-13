package com.cafeina.tcpmon;

import picocli.CommandLine;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TcpMonTlsCommand()).execute(args);
        System.exit(exitCode);
    }
}
