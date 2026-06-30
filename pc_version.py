import sys
import os
import http.server
import socketserver
import threading
import socket
import tkinter as tk
from tkinter import ttk, filedialog
import json
import time

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
        # Enviar logs a la UI
        if hasattr(self.server, 'app_instance'):
            self.server.app_instance.add_log(args[0], args[1], args[2])

class XLocalHostPC:
    def __init__(self, root):
        self.root = root
        self.root.title("X-LOCALHOST | PC Workstation Pro")
        self.root.geometry("900x650")
        self.root.configure(bg=BG_DEEP)
        
        self.server_thread = None
        self.httpd = None
        self.is_running = False
        self.root_folder = os.getcwd()
        self.port = 8080
        
        self.setup_ui()
        
    def setup_ui(self):
        # Estilo corporativo
        style = ttk.Style()
        style.theme_use('default')
        style.configure("Elite.TFrame", background=BG_DEEP)
        style.configure("Panel.TFrame", background=BG_PANEL, borderwidth=1, relief="solid")
        
        # Main Layout
        main_container = tk.Frame(self.root, bg=BG_DEEP, padx=30, pady=30)
        main_container.pack(fill="both", expand=True)
        
        # Header
        header = tk.Frame(main_container, bg=BG_DEEP)
        header.pack(fill="x", pady=(0, 30))
        
        title_label = tk.Label(header, text="X-LOCALHOST", bg=BG_DEEP, fg=TEXT_PRIMARY, 
                               font=("Segoe UI", 24, "bold"))
        title_label.pack(side="left")
        
        self.status_indicator = tk.Label(header, text="● STANDBY", bg=BG_DEEP, fg=TEXT_SEC, 
                                        font=("Segoe UI", 10, "bold"))
        self.status_indicator.pack(side="left", padx=20, pady=(10, 0))
        
        # Dashboard Cards Row
        cards_frame = tk.Frame(main_container, bg=BG_DEEP)
        cards_frame.pack(fill="x", pady=(0, 20))
        
        # IP Card
        ip_card = tk.Frame(cards_frame, bg=BG_PANEL, highlightbackground=BORDER_ELITE, 
                          highlightthickness=1, padx=20, pady=20)
        ip_card.pack(side="left", fill="both", expand=True, padx=(0, 10))
        tk.Label(ip_card, text="LOCAL IP ADDRESS", bg=BG_PANEL, fg=TEXT_SEC, font=("Segoe UI", 8, "bold")).pack(anchor="w")
        self.ip_label = tk.Label(ip_card, text=self.get_ip(), bg=BG_PANEL, fg=ACC_NEON, font=("Segoe UI", 18, "bold"))
        self.ip_label.pack(anchor="w")
        
        # Controls Card
        ctrl_card = tk.Frame(cards_frame, bg=BG_PANEL, highlightbackground=BORDER_ELITE, 
                            highlightthickness=1, padx=20, pady=20)
        ctrl_card.pack(side="left", fill="both", expand=True, padx=(10, 0))
        
        self.btn_toggle = tk.Button(ctrl_card, text="START SERVER", bg=ACC_SUCCESS, fg=BG_DEEP, 
                                   font=("Segoe UI", 10, "bold"), command=self.toggle_server,
                                   activebackground=ACC_NEON, relief="flat", padx=20)
        self.btn_toggle.pack(side="right")
        
        tk.Label(ctrl_card, text="DIRECTORY", bg=BG_PANEL, fg=TEXT_SEC, font=("Segoe UI", 8, "bold")).pack(anchor="w")
        self.dir_label = tk.Label(ctrl_card, text=self.root_folder, bg=BG_PANEL, fg=TEXT_PRIMARY, 
                                 font=("Segoe UI", 10), wraplength=200)
        self.dir_label.pack(anchor="w")
        self.dir_label.bind("<Button-1>", lambda e: self.change_dir())
        
        # Logs Section
        tk.Label(main_container, text="NETWORK TRAFFIC", bg=BG_DEEP, fg=ACC_NEON, 
                 font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(20, 10))
        
        log_frame = tk.Frame(main_container, bg=BG_PANEL, highlightbackground=BORDER_ELITE, highlightthickness=1)
        log_frame.pack(fill="both", expand=True)
        
        self.log_list = tk.Text(log_frame, bg=BG_PANEL, fg=TEXT_PRIMARY, font=("Consolas", 10),
                               relief="flat", padx=15, pady=15, state="disabled")
        self.log_list.pack(fill="both", expand=True)
        
        # Footer
        footer = tk.Label(main_container, text="--flinger apps | Flinger Apps Corporation", 
                         bg=BG_DEEP, fg=TEXT_SEC, font=("Segoe UI", 8))
        footer.pack(pady=(20, 0))

    def get_ip(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except: return "127.0.0.1"

    def change_dir(self):
        path = filedialog.askdirectory()
        if path:
            self.root_folder = path
            self.dir_label.config(text=path)

    def toggle_server(self):
        if not self.is_running:
            self.start_server()
        else:
            self.stop_server()

    def start_server(self):
        try:
            os.chdir(self.root_folder)
            self.httpd = socketserver.TCPServer(("", self.port), EliteServer)
            self.httpd.app_instance = self
            self.server_thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
            self.server_thread.start()
            
            self.is_running = True
            self.btn_toggle.config(text="STOP SERVER", bg=ACC_ERROR, fg=TEXT_PRIMARY)
            self.status_indicator.config(text="● SERVER LIVE", fg=ACC_SUCCESS)
            self.add_log("SYSTEM", f"Server started at {self.get_ip()}:{self.port}", "200")
        except Exception as e:
            self.add_log("ERROR", str(e), "500")

    def stop_server(self):
        if self.httpd:
            self.httpd.shutdown()
            self.httpd.server_close()
        self.is_running = False
        self.btn_toggle.config(text="START SERVER", bg=ACC_SUCCESS, fg=BG_DEEP)
        self.status_indicator.config(text="● STANDBY", fg=TEXT_SEC)
        self.add_log("SYSTEM", "Server stopped", "---")

    def add_log(self, method, path, status):
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
