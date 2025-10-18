# MovieLocal 🎬

Sistema completo de streaming local de filmes e séries com dois aplicativos Android conectados via rede local.

## 📱 Aplicativos

### 1. MovieServer
Servidor local que armazena e transmite filmes/séries do armazenamento interno do dispositivo.

**Características:**
- Servidor HTTP local (porta 8080)
- Scanneia automaticamente pastas de filmes e séries
- Suporte a múltiplos formatos: MP4, MKV, AVI, WEBM
- API REST para listagem de conteúdo
- Interface Material 3 para gerenciamento
- Notificação de foreground quando ativo

### 2. MovieClient
Cliente com interface estilo Netflix/Stremio para assistir conteúdo.

**Características:**
- UI moderna estilo Netflix com Material 3
- Tema escuro com cores inspiradas em Netflix
- Navegação por filmes e séries
- Busca em tempo real
- Player de vídeo com ExoPlayer
- Suporte a séries com temporadas e episódios
- Modo landscape automático para reprodução

## 🚀 Como Usar

### Configuração do Servidor

1. **Instale o MovieServer** no dispositivo que terá os filmes
2. **Organize seus arquivos** em:
   ```
   Android/data/com.movielocal.server/files/
   ├── Movies/
   │   ├── Filme1/
   │   │   ├── filme1.mp4
   │   │   └── poster.jpg (opcional)
   │   └── Filme2/
   │       ├── filme2.mkv
   │       └── thumb.jpg (opcional)
   └── Series/
       ├── Serie1/
       │   ├── poster.jpg (opcional)
       │   ├── Season1/
       │   │   ├── episode1.mp4
       │   │   ├── episode2.mp4
       │   │   └── ...
       │   └── Season2/
       │       ├── episode1.mp4
       │       └── ...
       └── Serie2/
           └── ...
   ```

3. **Inicie o servidor** no app
4. **Anote o endereço IP** mostrado (ex: `192.168.1.100:8080`)

### Configuração do Cliente

1. **Instale o MovieClient** no dispositivo onde assistirá
2. **Conecte à mesma rede Wi-Fi** do servidor
3. **Abra o app** e insira o endereço do servidor
4. **Navegue e assista** seu conteúdo!

## 🏗️ Tecnologias

### MovieServer
- **Kotlin** - Linguagem principal
- **Jetpack Compose** - UI moderna
- **Material 3** - Design System
- **NanoHTTPD** - Servidor HTTP leve
- **Gson** - Serialização JSON
- **Foreground Service** - Servidor persistente

### MovieClient
- **Kotlin** - Linguagem principal
- **Jetpack Compose** - UI moderna
- **Material 3** - Design System
- **Retrofit** - Cliente HTTP
- **ExoPlayer** - Player de vídeo
- **Coil** - Carregamento de imagens
- **Navigation Compose** - Navegação

## 📦 Build

### Via Android Studio
1. Clone o repositório
2. Abra no Android Studio
3. Sync Gradle
4. Build → Build Bundle(s) / APK(s) → Build APK(s)

### Via Terminal
```bash
# Build ambos os apps
./gradlew assembleDebug

# Build apenas o servidor
./gradlew :movieserver:assembleDebug

# Build apenas o cliente
./gradlew :movieclient:assembleDebug
```

### Via GitHub Actions
- Push para a branch `main` ou qualquer branch `capy/**`
- Os APKs serão gerados automaticamente
- Baixe os artifacts da aba Actions

## 🔧 Configuração de Desenvolvimento

### Requisitos
- Android Studio Hedgehog | 2023.1.1 ou superior
- JDK 17
- Android SDK 34
- Gradle 8.2+

### Estrutura do Projeto
```
MovieLocal/
├── movieserver/          # App servidor
│   ├── src/main/
│   │   ├── java/com/movielocal/server/
│   │   │   ├── server/   # Lógica do servidor HTTP
│   │   │   ├── models/   # Modelos de dados
│   │   │   └── ui/       # Interface do usuário
│   │   └── res/          # Recursos
│   └── build.gradle.kts
├── movieclient/          # App cliente
│   ├── src/main/
│   │   ├── java/com/movielocal/client/
│   │   │   ├── data/     # API e repositório
│   │   │   ├── ui/       # Interface do usuário
│   │   │   └── player/   # Player de vídeo
│   │   └── res/          # Recursos
│   └── build.gradle.kts
├── build.gradle.kts      # Build raiz
└── settings.gradle.kts   # Configurações do projeto
```

## 📡 API do Servidor

### Endpoints

#### Health Check
```
GET /api/health
Response: {
  "status": "ok",
  "version": "1.0",
  "serverTime": 1234567890
}
```

#### Listar Conteúdo
```
GET /api/content
Response: {
  "movies": [...],
  "series": [...]
}
```

#### Stream de Vídeo
```
GET /api/stream/{file_path}
Response: Video stream
```

#### Thumbnail
```
GET /api/thumbnail/{file_path}
Response: Image
```

## 🎨 Design

O MovieClient utiliza um tema inspirado no Netflix:
- **Cor Primária**: `#E50914` (Netflix Red)
- **Background**: `#141414` (Netflix Black)
- **Surface**: `#1F1F1F` (Netflix Dark Gray)
- **Tipografia**: Material Design Typography
- **Componentes**: Material 3

## 🔒 Permissões

### MovieServer
- `INTERNET` - Servidor HTTP
- `ACCESS_NETWORK_STATE` - Verificar conexão
- `READ_EXTERNAL_STORAGE` - Acessar arquivos (Android < 13)
- `READ_MEDIA_VIDEO` - Acessar vídeos (Android 13+)
- `FOREGROUND_SERVICE` - Manter servidor ativo
- `POST_NOTIFICATIONS` - Notificações (Android 13+)

### MovieClient
- `INTERNET` - Conectar ao servidor
- `ACCESS_NETWORK_STATE` - Verificar conexão

## 🐛 Troubleshooting

### Servidor não conecta
- Verifique se ambos os dispositivos estão na mesma rede Wi-Fi
- Certifique-se de que o servidor está rodando
- Tente desabilitar firewall/VPN

### Vídeo não carrega
- Verifique se o formato do arquivo é suportado
- Confirme que o arquivo não está corrompido
- Teste com outro arquivo

### Thumbnails não aparecem
- Nomeie as imagens como `poster.jpg`, `poster.png`, `thumb.jpg` ou `thumb.png`
- Coloque as imagens nas pastas dos filmes/séries

## 📄 Licença

Este projeto é open source e está disponível sob a licença MIT.

## 🤝 Contribuindo

Contribuições são bem-vindas! Sinta-se livre para abrir issues e pull requests.

## ✨ Features Futuras

- [ ] Suporte a legendas
- [ ] Download de metadados automático (TMDb)
- [ ] Histórico de reprodução
- [ ] Sincronização entre dispositivos
- [ ] Suporte a múltiplos servidores
- [ ] Cast para TV
- [ ] Downloads offline
- [ ] Perfis de usuário

---

**Desenvolvido com ❤️ usando Kotlin e Jetpack Compose**
