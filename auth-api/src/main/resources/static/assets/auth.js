'use strict';

function setupRegisterForm(endpoint) {
    const form = document.getElementById('register-form');
    if (!form) return;
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const data = Object.fromEntries(new FormData(form).entries());
        try {
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            const body = await response.json().catch(() => ({}));
            if (!response.ok) {
                throw new Error(body.error || 'Ошибка регистрации');
            }
            document.getElementById('message').textContent = 'Регистрация успешна. Теперь войдите.';
            form.reset();
        } catch (err) {
            document.getElementById('message').textContent = err.message;
        }
    });
}

function setupLoginForm(endpoint) {
    const form = document.getElementById('login-form');
    if (!form) return;
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const data = Object.fromEntries(new FormData(form).entries());
        try {
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            const body = await response.json();
            if (!response.ok) {
                throw new Error(body.error || 'Ошибка авторизации');
            }
            localStorage.setItem('access_token', body.accessToken);
            localStorage.setItem('refresh_token', body.refreshToken || '');
            window.location.href = '/dashboard.html';
        } catch (err) {
            document.getElementById('message').textContent = err.message;
        }
    });
}

async function loadDashboard() {
    const token = localStorage.getItem('access_token');
    if (!token) {
        window.location.href = '/login.html';
        return;
    }
    try {
        const response = await fetch('/me', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (response.status === 401) {
            localStorage.removeItem('access_token');
            window.location.href = '/login.html';
            return;
        }
        const body = await response.json();
        document.getElementById('welcome').textContent = `Добро пожаловать, ${body.email || body.sub}`;
        document.getElementById('roles').textContent = JSON.stringify(body.roles, null, 2);
    } catch (err) {
        document.getElementById('welcome').textContent = 'Не удалось загрузить данные';
    }
}

function logout() {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    window.location.href = '/login.html';
}
