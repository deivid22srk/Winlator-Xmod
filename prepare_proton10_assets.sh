#!/bin/bash

# Script para preparar assets do Proton 10.0 ARM64EC
# Usage: ./prepare_proton10_assets.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSETS_DIR="$SCRIPT_DIR/app/src/main/assets"
TEMP_DIR="/tmp/proton10_preparation"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Verificar se arrays.xml foi atualizado
check_arrays_updated() {
    log_info "Verificando se o suporte ao Proton foi implementado..."
    
    if grep -q "CONTENT_TYPE_PROTON" "$SCRIPT_DIR/app/src/main/java/com/winlator/cmod/contents/ContentProfile.java"; then
        log_success "ContentProfile.java tem suporte ao CONTENT_TYPE_PROTON"
        return 0
    else
        log_error "ContentProfile.java ainda não tem suporte ao CONTENT_TYPE_PROTON"
        echo "  O suporte ao Proton é necessário para evitar crashes na instalação!"
        echo "  Verifique se as correções foram aplicadas:"
        echo "    - CONTENT_TYPE_PROTON no enum"
        echo "    - Proton parsing no ContentsManager"
        return 1
    fi
}

# Criar diretório temporário
setup_temp_dir() {
    log_info "Criando diretório temporário..."
    rm -rf "$TEMP_DIR"
    mkdir -p "$TEMP_DIR"
}

# Verificar assets existentes
check_existing_assets() {
    log_info "Verificando assets existentes..."
    
    local found_pattern=false
    
    if [ -f "$ASSETS_DIR/proton-9.0-arm64ec_container_pattern.tzst" ]; then
        log_success "Found proton-9.0-arm64ec container pattern"
        found_pattern=true
    fi
    
    if [ -f "$ASSETS_DIR/proton-9.0-x86_64_container_pattern.tzst" ]; then
        log_success "Found proton-9.0-x86_64 container pattern"
        found_pattern=true
    fi
    
    if [ "$found_pattern" = false ]; then
        log_error "Nenhum container pattern do Proton 9.0 encontrado!"
        log_error "Verifique se os assets do Proton 9.0 estão presentes."
        return 1
    fi
}

# Criar container pattern base para Proton 10
create_base_container_patterns() {
    log_info "Criando container patterns base para Proton 10..."
    
    # Proton 10 x86_64
    if [ -f "$ASSETS_DIR/proton-9.0-x86_64_container_pattern.tzst" ]; then
        cp "$ASSETS_DIR/proton-9.0-x86_64_container_pattern.tzst" \
           "$ASSETS_DIR/proton-10.0-x86_64_container_pattern.tzst"
        log_success "Created proton-10.0-x86_64_container_pattern.tzst"
    fi
    
    # Proton 10 ARM64EC
    if [ -f "$ASSETS_DIR/proton-9.0-arm64ec_container_pattern.tzst" ]; then
        cp "$ASSETS_DIR/proton-9.0-arm64ec_container_pattern.tzst" \
           "$ASSETS_DIR/proton-10.0-arm64ec_container_pattern.tzst"
        log_success "Created proton-10.0-arm64ec_container_pattern.tzst"
    fi
}

# Função para baixar assets oficiais (placeholder)
download_official_assets() {
    log_info "Verificando disponibilidade de assets oficiais..."
    
    # URLs potenciais (podem precisar ser atualizadas)
    PROTON_RELEASE_URL="https://api.github.com/repos/ValveSoftware/Proton/releases/latest"
    
    log_warning "Download automático não implementado ainda."
    log_info "Para obter assets reais do Proton 10.0:"
    echo ""
    echo "Opção 1 - Valve Proton:"
    echo "  1. Acesse: https://github.com/ValveSoftware/Proton/releases"
    echo "  2. Baixe a versão Proton 10.0 para ARM64"
    echo "  3. Extraia e crie container pattern"
    echo ""
    echo "Opção 2 - Proton-GE:"
    echo "  1. Acesse: https://github.com/GloriousEggroll/proton-ge-custom/releases"
    echo "  2. Procure por builds ARM64EC"
    echo ""
    echo "Opção 3 - Steam Installation:"
    echo "  1. No Steam, force download do Proton 10.0"
    echo "  2. Localize em ~/.steam/steam/steamapps/common/Proton\\ 10.0/"
}

# Verificar DXVK compatibility
check_dxvk_compatibility() {
    log_info "Verificando compatibilidade DXVK..."
    
    local dxvk_arrays="$SCRIPT_DIR/app/src/main/res/values/arrays.xml"
    
    # Versões ARM64EC disponíveis
    local arm64ec_versions=(
        "1.10.3-arm64ec-async"
        "2.3.1-arm64ec-gplasync"
        "2.4-gplasync"
        "2.4-24-gplasync"
        "2.4.1-gplasync"
    )
    
    log_info "Versões DXVK ARM64EC disponíveis:"
    for version in "${arm64ec_versions[@]}"; do
        if grep -q "$version" "$dxvk_arrays"; then
            log_success "  ✓ $version"
        else
            log_warning "  ? $version (não encontrado em arrays.xml)"
        fi
    done
}

# Criar estrutura de teste
create_test_structure() {
    log_info "Criando estrutura de teste..."
    
    cat > "$TEMP_DIR/test_install.md" << 'EOF'
# Teste de Instalação Proton 10.0 ARM64EC

## Passos para Testar

1. **Build do Projeto**
   ```bash
   ./gradlew assembleDebug
   ```

2. **Instalar no Dispositivo**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Verificar Versões Disponíveis**
   - Abra o Winlator
   - Vá para "Containers" > "Create New"
   - Verifique se "proton-10.0-arm64ec" aparece na lista

4. **Teste de Container**
   - Crie um container com proton-10.0-arm64ec
   - Verifique se a instalação é bem-sucedida
   - Teste com um jogo simples

## Troubleshooting

- **Erro "Version not found"**: Verificar arrays.xml
- **Erro "Missing assets"**: Verificar container patterns
- **Erro "Installation failed"**: Verificar logs no Logcat

## Logs Úteis

```bash
# Filtrar logs do Winlator
adb logcat | grep -E "(Winlator|ContentsManager|WineInfo)"
```
EOF

    log_success "Documentação de teste criada em $TEMP_DIR/test_install.md"
}

# Função principal
main() {
    echo "=========================================="
    echo "  Preparação Proton 10.0 ARM64EC Assets  "
    echo "=========================================="
    echo ""
    
    # Verificações
    if ! check_arrays_updated; then
        log_error "Pré-requisitos não atendidos. Saindo."
        exit 1
    fi
    
    setup_temp_dir
    check_existing_assets || exit 1
    
    # Ações
    create_base_container_patterns
    check_dxvk_compatibility
    download_official_assets
    create_test_structure
    
    echo ""
    echo "=========================================="
    echo "              RESUMO                      "
    echo "=========================================="
    
    log_success "✓ Suporte ao CONTENT_TYPE_PROTON implementado"
    log_success "✓ Container patterns base criados"
    log_warning "⚠ Assets reais do Proton 10 precisam ser obtidos manualmente"
    
    echo ""
    echo "PRÓXIMOS PASSOS:"
    echo "1. Obter assets reais do Proton 10.0 (ver instruções acima)"
    echo "2. Substituir container patterns base por versões reais"
    echo "3. Build e teste no dispositivo ARM64"
    echo "4. Validar compatibilidade com jogos"
    
    echo ""
    log_info "Documentação de teste disponível em:"
    echo "  $TEMP_DIR/test_install.md"
}

# Executar se chamado diretamente
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi