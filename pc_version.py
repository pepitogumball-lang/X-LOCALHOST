import sys
import os
import http.server
import socketserver
import threading
import socket
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import json
import time
import psutil

# ── Corporate Elite Palette ──────────────────────────────────────────────────
BG_DEEP      = "#090B0D"
BG_PANEL     = "#121417"
BG_SURFACE   = "#1A1D21"
ACC_NEON     = "#00D4FF"
ACC_SUCCESS  = "#00FF85"
ACC_ERROR    = "#FF3B30"
TEXT_PRIMARY = "#F0F2F5"
TEXT_SEC     = "#9BA3AF"
BORDER_ELITE = "#2D333B"

class EliteServer(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        if hasattr(self.server, 'app_instance'):
            self.server.app_instance.add_log(args[0], args[1], args[2])

class XLocalHostPC:
    def __init__(self, root):
        self.root = root
        self.root.title("X-LOCALHOST | PC Workstation Pro")
        self.root.geometry("950x700")
        self.root.configure(bg=BG_DEEP)
        
        self.server_thread = None
        self.httpd = None
        self.is_running = False
        self.root_folder = os.getcwd()
        self.port = 8080
        self.total_requests = 0
        self.enable_cors = tk.BooleanVar(value=True)
        self.enable_sqlite = tk.BooleanVar(value=True)
        
        self.setup_ui()
        self.update_metrics_loop()
        self.pulse_status_loop()
        
    def setup_ui(self):
        # Main Layout
        main_container = tk.Frame(self.root, bg=BG_DEEP, padx=30, pady=30)
        main_container.pack(fill="both", expand=True)
        
        # Header
        header = tk.Frame(main_container, bg=BG_DEEP)
        header.pack(fill="x", pady=(0, 30))
        
        title_label = tk.Label(header, text="X-LOCALHOST", bg=BG_DEEP, fg=TEXT_PRIMARY, 
                               font=("Segoe UI", 24, "bold"), letterspacing=2)
        title_label.pack(side="left")
        
        self.status_indicator = tk.Label(header, text="● STANDBY", bg=BG_DEEP, fg=TEXT_SEC, 
                                        font=("Segoe UI", 10, "bold"))
        self.status_indicator.pack(side="left", padx=20, pady=(10, 0))
        
        # Dashboard Row 1: Status & URL
        status_row = tk.Frame(main_container, bg=BG_DEEP)
        status_row.pack(fill="x", pady=(0, 20))
        
        self.url_card = tk.Frame(status_row, bg=BG_PANEL, highlightbackground=BORDER_ELITE, 
                                highlightthickness=1, padx=20, pady=20)
        self.url_card.pack(side="left", fill="both", expand=True, padx=(0, 10))
        
        tk.Label(self.url_card, text="LOCAL ACCESS URL", bg=BG_PANEL, fg=TEXT_SEC, font=("Segoe UI", 8, "bold")).pack(anchor="w")
        self.ip_label = tk.Label(self.url_card, text=f"http://{self.get_ip()}:{self.port}", bg=BG_PANEL, fg=ACC_NEON, 
                                font=("Segoe UI", 18, "bold"), cursor="hand2")
        self.ip_label.pack(anchor="w")
        self.ip_label.bind("<Button-1>", lambda e: self.copy_url())
        
        ctrl_card = tk.Frame(status_row, bg=BG_PANEL, highlightbackground=BORDER_ELITE, 
                            highlightthickness=1, padx=20, pady=20)
        ctrl_card.pack(side="left", fill="both", expand=False, width=250, padx=(10, 0))
        
        self.btn_toggle = tk.Button(ctrl_card, text="START SERVER", bg=ACC_SUCCESS, fg=BG_DEEP, 
                                   font=("Segoe UI", 10, "bold"), command=self.toggle_server,
                                   activebackground=ACC_NEON, relief="flat", padx=20, pady=10)
        self.btn_toggle.pack(expand=True)
        
        # Dashboard Row 2: Metrics
        metrics_row = tk.Frame(main_container, bg=BG_DEEP)
        metrics_row.pack(fill="x", pady=(0, 20))
        
        # RAM Card
        self.ram_card = self.create_metric_card(metrics_row, "RAM USAGE", "0%", ACC_NEON)
        self.ram_card.pack(side="left", fill="both", expand=True, padx=(0, 10))
        
        # CPU/Storage Card
        self.storage_card = self.create_metric_card(metrics_row, "DISK USAGE", "0%", ACC_SUCCESS)
        self.storage_card.pack(side="left", fill="both", expand=True, padx=(10, 10))
        
        # Requests Card
        self.req_card = self.create_metric_card(metrics_row, "TOTAL REQUESTS", "0", ACC_NEON)
        self.req_card.pack(side="left", fill="both", expand=True, padx=(10, 0))

        # Directory Control
        dir_frame = tk.Frame(main_container, bg=BG_PANEL, highlightbackground=BORDER_ELITE, highlightthickness=1, padx=15, pady=15)
        dir_frame.pack(fill="x", pady=(0, 20))
        
        tk.Label(dir_frame, text="ROOT DIRECTORY", bg=BG_PANEL, fg=TEXT_SEC, font=("Segoe UI", 8, "bold")).pack(side="left")
        self.dir_label = tk.Label(dir_frame, text=self.root_folder, bg=BG_PANEL, fg=TEXT_PRIMARY, 
                                 font=("Segoe UI", 10, "bold"), cursor="hand2")
        self.dir_label.pack(side="left", padx=20)
        self.dir_label.bind("<Button-1>", lambda e: self.change_dir())
        
        tk.Label(dir_frame, text="[EDIT]", bg=BG_PANEL, fg=ACC_NEON, font=("Segoe UI", 8, "bold"), cursor="hand2").pack(side="right")

        # Options Row
        options_row = tk.Frame(main_container, bg=BG_DEEP)
        options_row.pack(fill="x", pady=(0, 20))
        
        tk.Checkbutton(options_row, text="ENABLE CORS (*)", variable=self.enable_cors, bg=BG_DEEP, fg=TEXT_PRIMARY, 
                       selectcolor=BG_PANEL, activebackground=BG_DEEP, activeforeground=ACC_NEON, 
                       font=("Segoe UI", 9)).pack(side="left", padx=(0, 20))
                       
        tk.Checkbutton(options_row, text="SQLITE API (EXPERIMENTAL)", variable=self.enable_sqlite, bg=BG_DEEP, fg=TEXT_PRIMARY, 
                       selectcolor=BG_PANEL, activebackground=BG_DEEP, activeforeground=ACC_NEON, 
                       font=("Segoe UI", 9)).pack(side="left")

        # Logs Section
        tk.Label(main_container, text="SYSTEM TERMINAL", bg=BG_DEEP, fg=ACC_NEON, 
                 font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(0, 10))
        
        log_frame = tk.Frame(main_container, bg="#000000", highlightbackground=BORDER_ELITE, highlightthickness=1)
        log_frame.pack(fill="both", expand=True)
        
        self.log_list = tk.Text(log_frame, bg="#000000", fg=ACC_SUCCESS, font=("Consolas", 10),
                               relief="flat", padx=15, pady=15, state="disabled")
        self.log_list.pack(fill="both", expand=True)
        
        # Footer
        footer = tk.Label(main_container, text="--flinger apps | Flinger Apps Corporation | Workstation Pro Edition", 
                         bg=BG_DEEP, fg=TEXT_SEC, font=("Segoe UI", 8))
        footer.pack(pady=(20, 0))

    def create_metric_card(self, parent, label, value, color):
        card = tk.Frame(parent, bg=BG_PANEL, highlightbackground=BORDER_ELITE, highlightthickness=1, padx=15, pady=15)
        tk.Label(card, text=label, bg=BG_PANEL, fg=TEXT_SEC, font=("Segoe UI", 8, "bold")).pack(anchor="w")
        val_label = tk.Label(card, text=value, bg=BG_PANEL, fg=TEXT_PRIMARY, font=("Segoe UI", 16, "bold"))
        val_label.pack(anchor="w", pady=(5, 0))
        
        # Progress Bar (Canvas)
        canvas = tk.Canvas(card, height=4, bg=BORDER_ELITE, highlightthickness=0)
        canvas.pack(fill="x", pady=(10, 0))
        
        card.val_label = val_label
        card.canvas = canvas
        card.color = color
        return card

    def update_metrics_loop(self):
        try:
            # RAM
            ram = psutil.virtual_memory()
            self.update_metric_ui(self.ram_card, f"{ram.percent}%", ram.percent)
            
            # Storage
            disk = psutil.disk_usage(self.root_folder)
            self.update_metric_ui(self.storage_card, f"{disk.percent}%", disk.percent)
            
            # Requests
            self.req_card.val_label.config(text=str(self.total_requests))
            
        except: pass
        self.root.after(3000, self.update_metrics_loop)

    def update_metric_ui(self, card, text, percent):
        card.val_label.config(text=text)
        card.canvas.delete("progress")
        width = card.canvas.winfo_width()
        if width > 1:
            fill_width = (percent / 100) * width
            card.canvas.create_rectangle(0, 0, fill_width, 4, fill=card.color, outline="", tags="progress")

    def pulse_status_loop(self):
        if self.is_running:
            current_color = self.status_indicator.cget("fg")
            next_color = ACC_SUCCESS if current_color == BG_PANEL else BG_PANEL
            self.status_indicator.config(fg=next_color)
        else:
            self.status_indicator.config(fg=TEXT_SEC)
        
        self.root.after(1000, self.pulse_status_loop)

    def get_ip(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except: return "127.0.0.1"

    def copy_url(self):
        url = f"http://{self.get_ip()}:{self.port}"
        self.root.clipboard_clear()
        self.root.clipboard_append(url)
        messagebox.showinfo("X-LOCALHOST", "URL copied to clipboard!")

    def change_dir(self):
        path = filedialog.askdirectory()
        if path:
            self.root_folder = path
            self.dir_label.config(text=path)
            self.add_log("SYSTEM", f"Directory changed to: {path}", "---")

    def toggle_server(self):
        if not self.is_running:
            self.start_server()
        else:
            self.stop_server()

    def start_server(self):
        try:
            os.chdir(self.root_folder)
            # Allow port reuse to avoid 'Address already in use'
            socketserver.TCPServer.allow_reuse_address = True
            
            # Customizing the server with options
            class ConfiguredServer(EliteServer):
                def end_headers(self):
                    if self.server.app_instance.enable_cors.get():
                        self.send_header('Access-Control-Allow-Origin', '*')
                        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
                        self.send_header('Access-Control-Allow-Headers', 'X-Requested-With, Content-Type')
                    super().end_headers()

            self.httpd = socketserver.TCPServer(("", self.port), ConfiguredServer)
            self.httpd.app_instance = self
            self.server_thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
            self.server_thread.start()
            
            self.is_running = True
            self.btn_toggle.config(text="STOP SERVER", bg=ACC_ERROR, fg=TEXT_PRIMARY)
            self.status_indicator.config(text="● SERVER LIVE", fg=ACC_SUCCESS)
            self.add_log("SYSTEM", f"Server online at http://{self.get_ip()}:{self.port}", "200")
        except Exception as e:
            self.add_log("ERROR", f"Failed to start: {str(e)}", "500")

    def stop_server(self):
        if self.httpd:
            self.httpd.shutdown()
            self.httpd.server_close()
        self.is_running = False
        self.btn_toggle.config(text="START SERVER", bg=ACC_SUCCESS, fg=BG_DEEP)
        self.status_indicator.config(text="● STANDBY", fg=TEXT_SEC)
        self.add_log("SYSTEM", "Server offline", "---")

    def add_log(self, method, path, status):
        self.total_requests += 1
        self.log_list.config(state="normal")
        timestamp = time.strftime("%H:%M:%S")
        log_entry = f"[{timestamp}] {method:7} {path:30} {status}\n"
        self.log_list.insert("end", log_entry)
        self.log_list.see("end")
        self.log_list.config(state="disabled")

if __name__ == "__main__":
    root = tk.Tk()
    app = XLocalHostPC(root)
    root.mainloop()
