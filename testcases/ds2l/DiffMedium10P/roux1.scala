import daisy.lang._
import Real._
import daisy.lang.Vector._

object roux1 {

	def roux1(x: Vector): Real = {
require(x >= -58.25 && x <= 61.32 && x.size(1000)
	 && x.specV(Set(((0, 0),(-40.97, 32.29)), ((1, 1),(-32.74, -8.56)), ((2, 3),(-46.95, -43.08)),
((9, 16),(-17.44, 20.55)), ((17, 17),(8.01, 34.84)), ((18, 19),(-24.27, 50.84)),
((20, 29),(-28.17, -17.36)), ((31, 31),(-51.95, -23.68)), ((32, 33),(-15.78, 0.79)),
((34, 34),(-18.35, 59.44)), ((35, 35),(-17.02, 46.13)), ((36, 38),(-33.91, 0.6)),
((39, 41),(-44.15, -35.26)), ((42, 42),(-43.0, 27.03)), ((43, 52),(-47.78, 58.06)),
((53, 53),(-10.56, 60.9)), ((54, 56),(-9.7, 8.03)), ((57, 61),(-10.6, 10.39)),
((62, 62),(-52.7, -45.28)), ((63, 69),(-1.45, 40.03)), ((70, 71),(-51.06, 33.34)),
((72, 81),(1.64, 6.75)), ((82, 83),(-40.67, -13.29)), ((84, 84),(22.3, 24.15)),
((86, 87),(-48.33, 46.53)), ((88, 97),(-53.53, 17.51)), ((134, 143),(-37.46, 16.51)),
((146, 147),(21.1, 45.68)), ((148, 149),(-46.22, -7.84)), ((150, 152),(-56.64, 53.01)),
((153, 154),(-41.67, 46.01)), ((155, 157),(-21.26, 26.66)), ((158, 158),(-36.16, 44.87)),
((159, 159),(-55.81, -54.16)), ((160, 169),(9.47, 10.89)), ((170, 175),(1.27, 51.02)),
((178, 183),(4.18, 40.37)), ((185, 185),(-7.9, 34.27)), ((186, 188),(-9.87, 24.94)),
((190, 199),(-50.21, 23.05)), ((263, 272),(12.76, 60.5)), ((277, 284),(-39.68, 20.59)),
((287, 291),(7.37, 33.81)), ((292, 301),(-48.33, -1.79)), ((302, 303),(16.71, 48.86)),
((304, 304),(-57.13, -7.19)), ((305, 307),(21.79, 59.72)), ((308, 308),(-32.15, 44.4)),
((309, 318),(-43.74, -1.77)), ((319, 321),(-25.31, 9.78)), ((322, 331),(-4.35, 30.73)),
((341, 350),(-14.5, 52.09)), ((432, 441),(24.83, 31.1)), ((442, 443),(-40.94, 32.55)),
((444, 449),(-23.2, 8.41)), ((450, 451),(1.31, 21.81)), ((452, 453),(48.17, 50.98)),
((454, 463),(-54.43, -51.95)), ((465, 465),(-51.01, 47.37)), ((466, 468),(15.67, 23.85)),
((469, 478),(-20.88, -11.74)), ((556, 557),(13.51, 56.26)), ((566, 575),(14.56, 41.58)),
((578, 587),(-20.43, 55.19)), ((589, 598),(-2.08, 28.77)), ((604, 613),(-38.35, 57.3)),
((631, 636),(-44.3, 55.81)), ((642, 651),(-38.64, 37.63)), ((652, 657),(-1.22, 34.79)),
((668, 677),(-25.73, -4.11)), ((728, 737),(-18.33, 32.0)), ((753, 762),(-24.78, 16.1)),
((763, 763),(19.58, 37.64)), ((764, 767),(13.48, 45.72)), ((768, 777),(-56.4, 28.52)),
((794, 803),(-40.18, 41.31)), ((804, 804),(11.66, 58.5)), ((805, 814),(-28.82, -11.93)),
((815, 824),(-13.27, 33.99)), ((904, 906),(-30.23, -4.29)), ((909, 910),(-19.81, 24.08)),
((911, 913),(-39.44, -26.2)), ((914, 917),(33.02, 51.83)), ((918, 926),(-23.75, 35.38)),
((927, 927),(-41.43, 48.24)), ((928, 928),(-40.67, 60.46)), ((930, 930),(37.08, 58.38)),
((931, 932),(7.26, 34.66)), ((933, 942),(1.53, 5.67)), ((958, 967),(-0.29, 6.32)),
((968, 968),(-12.61, 35.93)), ((969, 970),(28.1, 59.2)), ((971, 973),(-51.37, -21.25)),
((974, 975),(-10.96, 26.45)), ((976, 979),(11.86, 52.47)), ((981, 983),(-57.13, -12.22)),
((984, 993),(5.86, 6.38)), ((994, 994),(-50.76, -15.79)), ((995, 996),(-10.64, 4.05)),
((999, 999),(-27.22, 41.39))))
	)

        x.fold(0.0)((y: Real, i: Real) => {1.5 * i - 0.7 * y})
    }


}