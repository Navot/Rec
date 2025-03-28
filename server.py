from flask import Flask, request, render_template_string

app = Flask(__name__)

login_page = "<form method='POST'>Username: <input type='text' name='username'><br>Password: <input type='password' name='password'><br><input type='submit' value='Login'></form>"

def check_credentials(username, password):
    return username == 'admin' and password == 'admin'

@app.route('/', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        username = request.form['username']
        password = request.form['password']
        if check_credentials(username, password):
            return '<h1>Login Successful!</h1>'
        else:
            return '<h1>Login Failed. Try again.</h1>', 401
    return render_template_string(login_page)

if __name__ == '__main__':
    app.run(port=8081)
