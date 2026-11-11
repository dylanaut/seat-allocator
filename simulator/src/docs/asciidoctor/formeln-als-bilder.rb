# Setzt LaTeX-Formeln (latexmath) für das PDF als Bilder: Blöcke [latexmath] und Inline-Formeln stem:[...].
# Gerendert wird mit JLaTeXMath (reines Java, kein natives Werkzeug nötig); die Bilder landen in dem Verzeichnis
# des Attributs formel-dir. Nur aktiv für das Backend pdf, das HTML bleibt bei MathJax.

require 'digest'
require 'fileutils'
require 'java'

java.lang.System.setProperty('java.awt.headless', 'true')

java_import 'org.scilab.forge.jlatexmath.TeXConstants'
java_import 'org.scilab.forge.jlatexmath.TeXFormula'

module FormelnAlsBilder
  # Pixel je Schriftgröße beim Rendern; die Breite im PDF ergibt sich daraus und der Zielschrift in Punkt
  RENDER_GROESSE = 80.0
  SCHRIFT_INLINE = 12.0
  SCHRIFT_BLOCK = 13.5

  # Rendert die Formel als PNG und liefert Dateipfad sowie Breite und Höhe der Zielgröße in Punkt
  def self.rendern(latex, block, verzeichnis)
    FileUtils.mkdir_p verzeichnis
    stil = block ? TeXConstants::STYLE_DISPLAY : TeXConstants::STYLE_TEXT
    schrift = block ? SCHRIFT_BLOCK : SCHRIFT_INLINE
    datei = File.join(verzeichnis, "#{Digest::SHA1.hexdigest("#{block}:#{latex}")[0, 16]}.png")

    icon = TeXFormula.new(latex).createTeXIcon(stil, RENDER_GROESSE)
    icon.setInsets(java.awt.Insets.new(3, 3, 3, 3))
    breite = icon.getIconWidth
    hoehe = icon.getIconHeight

    unless File.exist? datei
      bild = java.awt.image.BufferedImage.new(breite, hoehe, java.awt.image.BufferedImage::TYPE_INT_ARGB)
      grafik = bild.createGraphics
      grafik.setRenderingHint(java.awt.RenderingHints::KEY_ANTIALIASING, java.awt.RenderingHints::VALUE_ANTIALIAS_ON)
      icon.setForeground(java.awt.Color::BLACK)
      icon.paintIcon(javax.swing.JLabel.new, grafik, 0, 0)
      grafik.dispose
      javax.imageio.ImageIO.write(bild, 'png', java.io.File.new(datei))
    end

    faktor = schrift / RENDER_GROESSE
    [datei, (breite * faktor).round(2), (hoehe * faktor).round(2)]
  end

  def self.aktiv?(dokument)
    dokument.backend == 'pdf' && dokument.attr?('formel-dir')
  end
end

# Blöcke [latexmath] ++++ ... ++++
class FormelBlockProzessor < Asciidoctor::Extensions::Treeprocessor
  def process(dokument)
    return unless FormelnAlsBilder.aktiv?(dokument)

    dokument.find_by(context: :stem).each do |block|
      latex = block.lines.join("\n").strip
      datei, breite, _hoehe = FormelnAlsBilder.rendern(latex, true, dokument.attr('formel-dir'))
      bild = Asciidoctor::Block.new(block.parent, :image, content_model: :empty,
        attributes: { 'target' => datei, 'alt' => latex, 'align' => 'center', 'pdfwidth' => "#{breite}pt" })
      eltern = block.parent.blocks
      eltern[eltern.index(block)] = bild
    end
    nil
  end
end

# Inline stem:[...]: Die Formeln werden vor dem Parsen durch Bild-Makros ersetzt. Dadurch gelten sie auch in Tabellenzellen.
class FormelInlinePreprozessor < Asciidoctor::Extensions::Preprocessor
  INLINE_STEM = /stem:\[((?:\\\]|[^\]])*)\]/

  def process(dokument, leser)
    return leser unless FormelnAlsBilder.aktiv?(dokument)

    zeilen = leser.read_lines.map do |zeile|
      zeile.gsub(INLINE_STEM) do
        latex = Regexp.last_match(1).gsub('\\]', ']').strip
        datei, breite, _hoehe = FormelnAlsBilder.rendern(latex, false, dokument.attr('formel-dir'))
        "image:#{datei}[Formel,pdfwidth=#{breite}pt]"
      end
    end
    leser.unshift_lines zeilen
    leser
  end
end

Asciidoctor::Extensions.register do
  preprocessor FormelInlinePreprozessor
  treeprocessor FormelBlockProzessor
end
