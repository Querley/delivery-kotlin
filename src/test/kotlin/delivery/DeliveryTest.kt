package delivery

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import java.nio.file.Files

class DeliveryTest {
    @get:Rule val temporaria = TemporaryFolder()
    private val pasta get() = temporaria.root.toPath()
    private fun sistema() = Delivery(pasta)
    private fun item(numero: Int = 1, descricao: String = "Pizza", preco: String = "45.00") =
        ItemCardapio(numero, descricao, preco.toBigDecimal())

    private fun preparar(): Delivery {
        val sistema = sistema()
        sistema.cadastrarRestaurante("Pizzaria", "pizza@exemplo.com", "Rua A, 10", listOf(item(), item(2, "Refrigerante", "10.00")))
        sistema.cadastrarCliente("Ana", "62999998888", "Rua B, 20")
        return sistema
    }

    private fun pedido(sistema: Delivery) = sistema.registrarPedido("pizza@exemplo.com", "62999998888", listOf(
        ItemPedido(1, 1, "Pizza", BigDecimal("45.00")),
        ItemPedido(2, 2, "Refrigerante", BigDecimal("10.00"))
    ))

    @Test fun cadastrosPersistemEImpedemDuplicidade() {
        val sistema = preparar()
        assertNotNull(sistema().entrarRestaurante(" PIZZA@EXEMPLO.COM "))
        assertEquals("Ana", sistema().entrarCliente("(62) 99999-8888")?.nome)
        assertThrows(IllegalArgumentException::class.java) {
            sistema.cadastrarRestaurante("Outra", "PIZZA@exemplo.com", "Rua C", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            sistema.cadastrarCliente("Outra", "(62) 99999-8888", "Rua C")
        }
        assertEquals(1, sistema.listarRestaurantes().size)
        assertTrue(Files.exists(pasta.resolve("restaurante_1.json")))
        assertTrue(Files.readString(pasta.resolve("clientes.json")).trim().startsWith("["))
        assertNull(sistema.entrarCliente("62988887777"))
        assertNull(sistema.entrarRestaurante("outro@exemplo.com"))
    }

    @Test fun cardapioPermiteAdicionarRemoverESalvaNoJson() {
        val sistema = preparar()
        sistema.adicionarItem("pizza@exemplo.com", item(3, "Suco", "6.50"))
        assertThrows(IllegalArgumentException::class.java) { sistema.adicionarItem("pizza@exemplo.com", item(3)) }
        sistema.removerItem("pizza@exemplo.com", 1)
        assertEquals(listOf(2, 3), sistema().entrarRestaurante("pizza@exemplo.com")!!.menu.map { it.numeroItem })
        assertThrows(IllegalArgumentException::class.java) { sistema.removerItem("pizza@exemplo.com", 99) }
        assertTrue(Files.readString(pasta.resolve("restaurante_1.json")).contains("\"numero_item\""))
    }

    @Test fun pedidoTemUmaLinhaPorItemComIdDataETotalCorretos() {
        val sistema = preparar()
        val pedido = pedido(sistema)
        assertEquals(2, pedido.size)
        assertEquals(1, pedido.map { it.idPedido }.distinct().size)
        assertTrue(pedido.first().idPedido.isNotBlank())
        assertTrue(pedido.first().dataHora.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
        assertEquals(BigDecimal("65.00"), pedido.sumOf { it.valorTotalItem })
        assertTrue(pedido.all { it.status == 0 && it.enderecoCliente == "Rua B, 20" })
        assertEquals(Arquivos.CABECALHO.joinToString(";"), Files.readAllLines(pasta.resolve("pedidos.csv")).first())
        assertEquals(pedido, sistema().pedidosCliente("62999998888", false))
    }

    @Test fun atualizaTodasAsLinhasEFiltraOsCincoStatus() {
        val sistema = preparar()
        val id = pedido(sistema).first().idPedido
        for (status in 0..4) {
            sistema.alterarStatus("pizza@exemplo.com", id, status)
            val linhas = sistema.pedidosRestaurante("pizza@exemplo.com", status)
            assertEquals(2, linhas.size)
            assertTrue(linhas.all { it.status == status })
            assertEquals(if (status < 4) 2 else 0, sistema.pedidosCliente("62999998888", false).size)
            assertEquals(if (status == 4) 2 else 0, sistema.pedidosCliente("62999998888", true).size)
        }
    }

    @Test fun filtraPorRestauranteEClienteESoAlteraPedidoProprio() {
        val sistema = preparar()
        sistema.cadastrarRestaurante("Lanches", "lanche@exemplo.com", "Rua C", listOf(item()))
        sistema.cadastrarCliente("Bruno", "62988887777", "Rua D")
        val id = pedido(sistema).first().idPedido
        val segundo = sistema.registrarPedido("lanche@exemplo.com", "62988887777", listOf(ItemPedido(1, 1, "Pizza", BigDecimal("45.00"))))
        assertNotEquals(id, segundo.first().idPedido)
        assertEquals(1, sistema.pedidosRestaurante("lanche@exemplo.com").size)
        assertEquals(1, sistema.pedidosCliente("62988887777", false).size)
        val antes = Files.readString(pasta.resolve("pedidos.csv"))
        assertThrows(IllegalArgumentException::class.java) { sistema.alterarStatus("lanche@exemplo.com", id, 4) }
        assertThrows(IllegalArgumentException::class.java) { sistema.alterarStatus("pizza@exemplo.com", id, 5) }
        assertThrows(IllegalArgumentException::class.java) { sistema.alterarStatus("pizza@exemplo.com", "inexistente", 1) }
        assertEquals(antes, Files.readString(pasta.resolve("pedidos.csv")))
        sistema.alterarStatus("pizza@exemplo.com", id, 4)
        assertTrue(sistema.pedidosCliente("62988887777", true).isEmpty())
    }

    @Test fun csvPreservaAcentosAspasPontoEVirgulaEQuebraDeLinha() {
        val sistema = sistema()
        val descricao = "Pizza; tamanho \"família\"\ncom borda"
        sistema.cadastrarRestaurante("Pizzaria São José", "pizza@exemplo.com", "Rua A", listOf(item(1, descricao, "3.33")))
        sistema.cadastrarCliente("João; Silva", "62999998888", "Rua \"B\"; casa 2")
        sistema.registrarPedido("pizza@exemplo.com", "62999998888", listOf(ItemPedido(1, 3, descricao, BigDecimal("3.33"))))
        val lido = sistema().pedidosCliente("62999998888", false).single()
        assertEquals(descricao, lido.descricaoItem)
        assertEquals("João; Silva", lido.nomeCliente)
        assertEquals("Rua \"B\"; casa 2", lido.enderecoCliente)
        assertEquals(BigDecimal("9.99"), lido.valorTotalItem)
    }

    @Test fun historicoContinuaAposRemoverItemEPedidoDesatualizadoNaoEntra() {
        val sistema = preparar()
        val anterior = pedido(sistema)
        sistema.removerItem("pizza@exemplo.com", 1)
        assertEquals(anterior, sistema.pedidosCliente("62999998888", false))
        assertThrows(IllegalArgumentException::class.java) { pedido(sistema) }
        sistema.adicionarItem("pizza@exemplo.com", item(preco = "50.00"))
        assertThrows(IllegalArgumentException::class.java) { pedido(sistema) }
        assertEquals(2, sistema.pedidosCliente("62999998888", false).size)
    }

    @Test fun entradasInvalidasNaoCriamPedidos() {
        val sistema = preparar()
        assertThrows(IllegalArgumentException::class.java) { sistema.registrarPedido("pizza@exemplo.com", "62999998888", emptyList()) }
        for (quantidade in listOf(-1, 0, 1000)) {
            assertThrows(IllegalArgumentException::class.java) {
                sistema.registrarPedido("pizza@exemplo.com", "62999998888", listOf(ItemPedido(1, quantidade, "Pizza", BigDecimal("45.00"))))
            }
        }
        assertFalse(Files.exists(pasta.resolve("pedidos.csv")))
        listOf("0", "-1", "1.999", "1000000").forEach {
            assertThrows(IllegalArgumentException::class.java) { validarPreco(it.toBigDecimal()) }
        }
        assertThrows(IllegalArgumentException::class.java) { normalizarEmail("sem-arroba") }
        assertThrows(IllegalArgumentException::class.java) { normalizarTelefone("abc62999998888") }
    }

    @Test fun arquivoDanificadoNaoEhSobrescrito() {
        val sistema = preparar()
        val arquivo = pasta.resolve("pedidos.csv")
        Files.writeString(arquivo, "cabecalho-invalido\nconteudo-original\n")
        assertThrows(IllegalArgumentException::class.java) { pedido(sistema) }
        assertEquals("cabecalho-invalido\nconteudo-original\n", Files.readString(arquivo))
    }
}
