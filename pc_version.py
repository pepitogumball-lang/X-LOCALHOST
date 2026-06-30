import http.server
import socketserver
import threading
import tkinter as tk
from tkinter import filedialog, ttk
import json
import os
import socket
import webbrowser

class CorporateServer(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        # Redirect logs to the UI
        message = "%s - - [%s] %s\n" % (self.client_address[0], self.log_date_time_string(), format % args)
        if hasattr(self.server, 'ui_callback'):
            self.server.ui_callback(message)

class XLocalHostPC:
    def __init__(self, root):
        self.root = root
        self.root.title("X-LOCALHOST | Flinger Apps Corp")
        self.root.geometry("800x600")
        self.root.configure(bg="#0D0F12")
        
        self.server_thread = None
        self.httpd = None
        self.is_running = False
        self.root_folder = os.getcwd()
        
        self.setup_ui()
        
    def setup_ui(self):
        # Style configuration
        style = ttk.Style()
        style.theme_use('clam')
        style.configure("TFrame", background="#0D0F12")
        style.configure("Card.TFrame", background="#161B22", borderwidth=1, relief="solid")
        style.configure("TLabel", background="#161B22", foreground="#E6EDF3", font=("Segoe UI", 10))
        style.configure("Title.TLabel", background="#0D0F12", foreground="#E6EDF3", font=("Segoe UI", 18, "bold"))
        style.configure("Status.TLabel", background="#0D0F12", foreground="#3FB950", font=("Segoe UI", 10, "bold"))
        
        # Header
        header = tk.Frame(self.root, bg="#0D0F12", pady=20)
        header.pack(fill="x", padx=30)
        
        tk.Label(header, text="X-LOCALHOST", bg="#0D0F12", fg="#E6EDF3", font=("Segoe UI", 22, "black")).pack(side="left")
        self.status_dot = tk.Label(header, text="●", bg="#0D0F12", fg="#8B949E", font=("Segoe UI", 14))
        self.status_dot.pack(side="left", padx=(15, 5))
        self.status_text = tk.Label(header, text="SERVER STANDBY", bg="#0D0F12", fg="#8B949E", font=("Segoe UI", 10, "bold"))
        self.status_text.pack(side="left")
        
        # Main Content (Dashboard)
        container = tk.Frame(self.root, bg="#0D0F12")
        container.pack(fill="both", expand=True, padx=30, pady=10)
        
        # Status Card
        status_card = tk.Frame(container, bg="#161B22", highlightbackground="#30363D", highlightthickness=1, padx=20, pady=20)
        status_card.pack(fill="x", pady=10)
        
        tk.Label(status_card, text="Server Control", bg="#161B22", fg="#8B949E", font=("Segoe UI", 10)).pack(anchor="w")
        
        ctrl_row = tk.Frame(status_card, bg="#161B22")
        ctrl_row.pack(fill="x", pady=(5, 0))
        
        self.ip_label = tk.Label(ctrl_row, text=f"Local IP: {self.get_ip()}", bg="#161B22", fg="#2F81F7", font=("Segoe UI", 14, "bold"))
        self.ip_label.pack(side="left")
        
        self.btn_toggle = tk.Button(ctrl_row, text="START SERVER", command=self.toggle_server, 
                                   bg="#238636", fg="white", font=("Segoe UI", 10, "bold"), 
                                   padx=20, pady=5, relief="flat", activebackground="#2ea043")
        self.btn_toggle.pack(side="right")
        
        # Folder Card
        folder_card = tk.Frame(container, bg="#161B22", highlightbackground="#30363D", highlightthickness=1, padx=20, pady=15)
        folder_card.pack(fill="x", pady=10)
        
        tk.Label(folder_card, text="Root Directory", bg="#161B22", fg="#8B949E", font=("Segoe UI", 10)).pack(anchor="w")
        
        path_row = tk.Frame(folder_card, bg="#161B22")
        path_row.pack(fill="x", pady=(5, 0))
        
        self.path_entry = tk.Entry(path_row, bg="#0D0F12", fg="#E6EDF3", insertbackground="white", borderwidth=0, font=("Segoe UI", 10))
        self.path_entry.insert(0, self.root_folder)
        self.path_entry.pack(side="left", fill="x", expand=True, padx=(0, 10), pady=5)
        
        tk.Button(path_row, text="BROWSE", command=self.browse_folder, bg="#21262D", fg="#E6EDF3", 
                  relief="flat", padx=15).pack(side="right")
        
        # Logs Section
        tk.Label(container, text="NETWORK LOGS", bg="#0D0F12", fg="#8B949E", font=("Segoe UI", 10, "bold")).pack(anchor="w", pady=(20, 5))
        
        log_frame = tk.Frame(container, bg="#0D0F12")
        log_frame.pack(fill="both", expand=True)
        
        self.log_area = tk.Text(log_frame, bg="#161B22", fg="#8B949E", font=("Consolas", 9), borderwidth=1, relief="solid", padx=10, pady=10)
        self.log_area.pack(side="left", fill="both", expand=True)
        
        scrollbar = ttk.Scrollbar(log_frame, orient="vertical", command=self.log_area.yview)
        scrollbar.pack(side="right", fill="y")
        self.log_area.configure(yscrollcommand=scrollbar.set)
        
    def get_ip(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except:
            return "127.0.0.1"

    def browse_folder(self):
        folder = filedialog.askdirectory()
        if folder:
            self.root_folder = folder
            self.path_entry.delete(0, tk.END)
            self.path_entry.insert(0, folder)

    def toggle_server(self):
        if not self.is_running:
            self.start_server()
        else:
            self.stop_server()

    def start_server(self):
        self.root_folder = self.path_entry.get()
        if not os.path.exists(self.root_folder):
            self.log_ui("Error: Path does not exist.\n")
            return
            
        os.chdir(self.root_folder)
        handler = CorporateServer
        self.httpd = socketserver.TCPServer(("", 8080), handler)
        self.httpd.ui_callback = self.log_ui
        
        self.server_thread = threading.Thread(target=self.httpd.serve_forever)
        self.server_thread.daemon = True
        self.server_thread.start()
        
        self.is_running = True
        self.btn_toggle.config(text="STOP SERVER", bg="#da3633", activebackground="#f85149")
        self.status_dot.config(fg="#3FB950")
        self.status_text.config(text="SERVER LIVE", fg="#3FB950")
        self.log_ui(f"Server started at http://{self.get_ip()}:8080\n")
        
    def stop_server(self):
        if self.httpd:
            self.httpd.shutdown()
            self.httpd.server_close()
        self.is_running = False
        self.btn_toggle.config(text="START SERVER", bg="#238636", activebackground="#2ea043")
        self.status_dot.config(fg="#8B949E")
        self.status_text.config(text="SERVER STANDBY", fg="#8B949E")
        self.log_ui("Server stopped.\n")

    def log_ui(self, message):
        self.log_area.insert(tk.END, message)
        self.log_area.see(tk.END)

if __name__ == "__main__":
    root = tk.Tk()
    app = XLocalHostPC(root)
    root.mainloop()
