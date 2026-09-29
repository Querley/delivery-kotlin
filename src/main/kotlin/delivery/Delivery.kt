package delivery

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class Delivery(pasta: Path) {
    private val arquivos = Arquivos(pasta)

    fun listarRestaurantes(): List<Restaurante> = arquivos.transacao {
        restaurantes().map { it.second }.sortedBy { it.nome.lowercase() }
    }

    fun entrarRestaurante(email: String): Restaurante? = arquivos.transacao {
        val chave = normalizarEmail(email)
        restaurantes().find { normalizarEmail(it.second.email) == chave }?.second
    }

    fun cadastrarRestaurante(nome: String, email: String, endereco: String, menu: List<ItemCardapio>): Restaurante = arquivos.transacao {
        require(nome.isNotBlank() && endereco.isNotBlank()) { "Nome e endereço são obrigatórios." }
        val chave = normalizarEmail(email)
        require(restaurantes().none { normalizarEmail(it.second.email) == chave }) { "E-mail já cadastrado." }
        require(menu.map { it.numeroItem }.distinct().size == menu.size) { "Número de item repetido." }
        val itens = menu.map { validarItem(it) }
        val restaurante = Restaurante(nome.trim(), chave, endereco.trim(), itens)
        salvarRestaurante(novoArquivoRestaurante(), restaurante)
        restaurante
    }

    fun adicionarItem(email: String, item: ItemCardapio) = arquivos.transacao {
        val (arquivo, restaurante) = requireNotNull(restaurantes().find { normalizarEmail(it.second.email) == normalizarEmail(email) }) { "Restaurante não encontrado." }
        require(restaurante.menu.none { it.numeroItem == item.numeroItem }) { "Número de item já cadastrado." }
        salvarRestaurante(arquivo, restaurante.copy(menu = restaurante.menu + validarItem(item)))
    }

    fun removerItem(email: String, numero: Int) = arquivos.transacao {
        val (arquivo, restaurante) = requireNotNull(restaurantes().find { normalizarEmail(it.second.email) == normalizarEmail(email) }) { "Restaurante não encontrado." }
        require(restaurante.menu.any { it.numeroItem == numero }) { "Item não encontrado." }
        salvarRestaurante(arquivo, restaurante.copy(menu = restaurante.menu.filterNot { it.numeroItem == numero }))
    }

    fun entrarCliente(telefone: String): Cliente? = arquivos.transacao {
        val chave = normalizarTelefone(telefone)
        clientes().find { normalizarTelefone(it.telefone) == chave }
    }

    fun cadastrarCliente(nome: String, telefone: String, endereco: String): Cliente = arquivos.transacao {
        require(nome.isNotBlank() && endereco.isNotBlank()) { "Nome e endereço são obrigatórios." }
        val chave = normalizarTelefone(telefone)
        val lista = clientes()
        require(lista.none { normalizarTelefone(it.telefone) == chave }) { "Telefone já cadastrado." }
        val cliente = Cliente(nome.trim(), chave, endereco.trim())
        salvarClientes(lista + cliente)
        cliente
    }

    fun registrarPedido(email: String, telefone: String, itens: List<ItemPedido>): List<LinhaPedido> = arquivos.transacao {
        require(itens.isNotEmpty()) { "O pedido precisa ter pelo menos um item." }
        require(itens.map { it.numeroItem }.distinct().size == itens.size) { "Agrupe os itens repetidos antes de confirmar." }
        val restaurante = requireNotNull(restaurantes().find { normalizarEmail(it.second.email) == normalizarEmail(email) }?.second) {
            "Restaurante não encontrado."
        }
        val cliente = requireNotNull(clientes().find { normalizarTelefone(it.telefone) == normalizarTelefone(telefone) }) {
            "Cliente não encontrado."
        }
        itens.forEach { item ->
            require(item.quantidade in 1..999) { "A quantidade deve estar entre 1 e 999." }
            val atual = requireNotNull(restaurante.menu.find { it.numeroItem == item.numeroItem }) { "O cardápio mudou. Refaça o pedido." }
            require(atual.preco.compareTo(item.valorUnitario) == 0 && atual.descricao == item.descricao) {
                "O cardápio mudou. Confira os preços e refaça o pedido."
            }
        }
        val lista = pedidos()
        val id = UUID.randomUUID().toString()
        val dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val novas = itens.map { item ->
            LinhaPedido(id, dataHora, normalizarEmail(restaurante.email), restaurante.nome,
                normalizarTelefone(cliente.telefone), cliente.nome, cliente.endereco,
                item.numeroItem, item.quantidade, item.descricao, item.valorUnitario.setScale(2), item.total, 0)
        }
        salvarPedidos(lista + novas)
        novas
    }

    fun pedidosRestaurante(email: String, status: Int? = null): List<LinhaPedido> = arquivos.transacao {
        require(status == null || status in 0..4) { "Status inválido." }
        pedidos().filter { it.emailRestaurante == normalizarEmail(email) && (status == null || it.status == status) }
            .sortedWith(compareBy({ it.status }, { it.dataHora }, { it.idPedido }))
    }

    fun pedidosCliente(telefone: String, finalizados: Boolean): List<LinhaPedido> = arquivos.transacao {
        pedidos().filter { it.telefoneCliente == normalizarTelefone(telefone) && (if (finalizados) it.status == 4 else it.status < 4) }
    }

    fun alterarStatus(email: String, id: String, status: Int) = arquivos.transacao {
        require(status in 0..4) { "Informe um status de 0 a 4." }
        val lista = pedidos()
        val chave = normalizarEmail(email)
        require(lista.any { it.idPedido == id && it.emailRestaurante == chave }) { "Pedido não encontrado neste restaurante." }
        salvarPedidos(lista.map { if (it.idPedido == id && it.emailRestaurante == chave) it.copy(status = status) else it })
    }

    private fun validarItem(item: ItemCardapio): ItemCardapio {
        require(item.numeroItem > 0) { "O número do item deve ser positivo." }
        require(item.descricao.isNotBlank()) { "Descrição obrigatória." }
        return item.copy(descricao = item.descricao.trim(), preco = validarPreco(item.preco))
    }
}
